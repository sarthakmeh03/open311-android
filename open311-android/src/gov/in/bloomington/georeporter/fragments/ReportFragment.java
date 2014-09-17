/**
 * @copyright 2012 City of Bloomington, Indiana
 * @license http://www.gnu.org/licenses/gpl.txt GNU/GPL, see LICENSE.txt
 * @author Cliff Ingham <inghamn@bloomington.in.gov>
 */
package gov.in.bloomington.georeporter.fragments;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import gov.in.bloomington.georeporter.R;
import gov.in.bloomington.georeporter.activities.ChooseLocationActivity;
import gov.in.bloomington.georeporter.activities.MainActivity;
import gov.in.bloomington.georeporter.activities.SavedReportsActivity;
import gov.in.bloomington.georeporter.models.Open311;
import gov.in.bloomington.georeporter.models.Open311Exception;
import gov.in.bloomington.georeporter.models.Preferences;
import gov.in.bloomington.georeporter.models.ServiceRequest;
import gov.in.bloomington.georeporter.util.Media;
import gov.in.bloomington.georeporter.util.Util;
import gov.in.bloomington.georeporter.util.json.JSONArray;
import gov.in.bloomington.georeporter.util.json.JSONException;
import gov.in.bloomington.georeporter.util.json.JSONObject;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import ch.boye.httpclientandroidlib.client.ClientProtocolException;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.internal.view.View_HasStateListenerSupport;
import com.google.android.gms.maps.model.LatLng;

  public class ReportFragment extends SherlockFragment {
	  
	  /**
     * Request for handling Photo attachments to the Service Request
     */
    public static final int MEDIA_REQUEST      = 0;
    /**
   * Request for handling lat, long, and address
   */
    public static final int LOCATION_REQUEST   = 1;

	
	private ServiceRequest sr;
	private Context context;
	private TextView serviceDescription,displayValue;
	private ImageView iv_media;
	private String mCode,mDatatype,mDescription;
	private Boolean mVariable;
	private Uri mImageUri;
	private EditText description_input;
	private JSONArray attributes = new JSONArray();
	private JSONObject attribute = new JSONObject();
	
	/***
	 * Each View populated is added to an respective array for retrieval of desired values 
	 * and posting data to service request on submission of report
	 */
	private ArrayList<View> siViews = new ArrayList<View>();
	private ArrayList<View> desViews = new ArrayList<View>();
	private ArrayList<CheckBox> miCheckBox = new ArrayList<CheckBox>();
		
  public ReportFragment(ServiceRequest mServiceRequest,Context ctx) {
	    
	    context = ctx;
		sr = mServiceRequest;

	}

  public ArrayList<String> labels = new ArrayList<String>(Arrays.asList(
        Open311.SERVICE_NAME,
        Open311.MEDIA,
        Open311.ADDRESS_STRING,
        Open311.DESCRIPTION
    ));

  @Override 
  public void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);


}
  
  @Override
	public void onSaveInstanceState(Bundle outState) {
	    super.onSaveInstanceState(outState);
	    outState.putString(ServiceRequest.SERVICE_REQUEST, sr.toString());
	}
	

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

	 /***
	  * Parent layout which contains Media,Location,Description layouts 
	  * as every endpoint contains these parameters
	  */
	 View view = inflater.inflate(R.layout.fragment_report, container, false); 
	 RelativeLayout parentLayout = (RelativeLayout) view.findViewById(R.id.parentLayout);
	 
	/***
	 * Attribute header after which every attribute will be inflated
	 */
	 LinearLayout childLayout = (LinearLayout) view.findViewById(R.id.report_attributes);
	 childLayout.setId(1);
	 
	 LinearLayout media = (LinearLayout) view.findViewById(R.id.media);	
	 /***
		 *  When endpoints do not support Media, we must remove the media layout
		 */
		 if ((sr.endpoint != null && !sr.endpoint.optBoolean(Open311.SUPPORTS_MEDIA))
	           || !Preferences.getCurrentServer(context).optBoolean(Open311.SUPPORTS_MEDIA)) {
	     
			 media.setVisibility(View.INVISIBLE);
			 
		 }else{
			 
			 iv_media = (ImageView) media.findViewById(R.id.img_media);
			 media.setOnClickListener(listener_media);		 
		 }
	 
	 
	 RelativeLayout location = (RelativeLayout) view.findViewById(R.id.location);
	 displayValue = (TextView) location.findViewById(R.id.displayValue);
	 location.setOnClickListener(listener_location);
	 
	 serviceDescription = (TextView) view.findViewById(R.id.tv_serviceDescription);
	 serviceDescription.setText(sr.service.optString(Open311.DESCRIPTION));
	 
	 description_input = (EditText) view.findViewById(R.id.et_description);	 
	 
     if(sr.hasAttributes()){
    	 
    	 loadAttributeEntryView(parentLayout,childLayout);
	 
      }else{
    	  
    	 childLayout.setVisibility(View.INVISIBLE);
    	 
     }
	 
	 view.findViewById(R.id.cancel_button).setOnClickListener(new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			
			Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
			
		}
	});
	 
	 view.findViewById(R.id.submit_button).setOnClickListener(new OnClickListener() {
		
		@Override
		public void onClick(View v) {
									
			try {
				//Put the description of problem and attribute values user has entered to the endpoint
				sr.post_data.put(Open311.DESCRIPTION, description_input.getText().toString());
				if(sr.hasAttributes()){
					loadAttributeEntryValue();
				}
					
			}catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
			new PostServiceRequestTask().execute();
			
			}
	});
	    
	 
	// For datetime attributes, we'll just pop open a date picker dialog
	 View date = inflater.inflate(R.layout.list_item_other, null);
	 
	 date.findViewById(R.id.attribute_layout).setOnClickListener(new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			DatePickerDialogFragment datePicker = new DatePickerDialogFragment(mCode);
            datePicker.show(getActivity().getSupportFragmentManager(), "datePicker");
		}
	});
	      
     	return view;
	
}
  
  private OnClickListener listener_media  = new OnClickListener(){

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.choose_media_source)
               .setPositiveButton(R.string.camera, new DialogInterface.OnClickListener() {
                   /**
                    * Start the camera activity
                    * 
                    * To avoid differences in non-google-provided camera activities,
                    * we should always tell the camera activity to explicitly save
                    * the file in a Uri of our choosing.
                    * 
                    * The camera activity may, or may not, also save an image file 
                    * in the gallery.  For now, I'm just not going to worry about
                    * creating duplicate files on people's phones.  Users can clean
                    * those up themselves, if they want.
                    */
                   public void onClick(DialogInterface dialog, int id) {
                       mImageUri = Media.getOutputMediaFileUri(Media.MEDIA_TYPE_IMAGE);
                       
                       Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                       i.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
                       startActivityForResult(i, MEDIA_REQUEST);
                   }
               })
               .setNeutralButton(R.string.gallery, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                       i.setType("image/*");
                       startActivityForResult(i, MEDIA_REQUEST);
                   }
               })
               .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                   }
               });
        AlertDialog alert = builder.create();
        alert.show();
	
		
	}
	  
  };
  
  private OnClickListener listener_location = new OnClickListener(){

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		
		Intent i = new Intent(getActivity(), ChooseLocationActivity.class);
		startActivityForResult(i, LOCATION_REQUEST);
			
	}
	  
  };
  
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
	    super.onActivityResult(requestCode, resultCode, data);
	    
	    if (resultCode == Activity.RESULT_OK) {
	        try {
  	        switch (requestCode) {
                  case MEDIA_REQUEST:
                	  
                      // Determine if this is from the camera or gallery
                      Uri imageUri = (mImageUri != null) ? mImageUri : data.getData();
                      sr.post_data.put(Open311.MEDIA, imageUri.toString());
                      if (imageUri != null) {  
                    			 	
                    			 Bitmap bmp = sr.getMediaBitmap(80, 80, context);
                    		     if (bmp != null) {
                    		        iv_media.setImageBitmap(bmp);
                    		     }
                          
                          mImageUri = null; // Remember to wipe it out, so we don't confuse camera and gallery
                      }
                      break;
                      
                  case LOCATION_REQUEST:
                    // The ChooseLocationActivity should put LATITUDE and LONGITUDE
                    // into the Intent data as type double
                    double latitude  = data.getDoubleExtra(Open311.LATITUDE, 0);
                    double longitude = data.getDoubleExtra(Open311.LONGITUDE, 0);
                    
                    if (!Double.isNaN(latitude) && !Double.isNaN(longitude)) {
                        displayValue.setText(String.format("%f, %f", latitude, longitude));
                    }
                    
                    sr.post_data.put(Open311.LATITUDE , latitude);
                    sr.post_data.put(Open311.LONGITUDE, longitude);
                    // Display the lat/long as text for now
                    // It will get replaced with the address when ReverseGeoCodingTask returns
                    new ReverseGeocodingTask().execute(new LatLng(latitude, longitude));
                    break;
                        
  	        }
  	        }catch (Exception e) {
				// TODO: handle exception
  	        	e.printStackTrace();
			}
	   }
	}
	
	/***
	 * Loop through all the values of attributes and add it the endpoint
	 * siCount,miCount,desCount maintains the number of respective attribute layouts 
	 */  
  private void loadAttributeEntryValue(){
			
			try {
				attributes = sr.service_definition.getJSONArray(Open311.ATTRIBUTES);
		 
		 for(int k=0,siCount=0,miCount=0,desCount=0;k<attributes.length();k++){
			   
				attribute  = attributes.getJSONObject(k);
				mCode      = attribute.getString(Open311.CODE);
				mDatatype  = attribute.optString(Open311.DATATYPE, Open311.STRING);
				JSONArray values = attribute.optJSONArray(Open311.VALUES);			
      	
			String key = String.format("%s[%s]", "attribute",mCode);
			
			if(mDatatype.equals(Open311.SINGLEVALUELIST)){
				
					View siView = siViews.get(siCount);
					
					RadioGroup siRadioGroup  = (RadioGroup) siView.findViewById(R.id.input2);
		        	
		        	int count = siRadioGroup.getChildCount();
	                for (int j = 0; j < count; j++) {
	                    RadioButton b = (RadioButton) siRadioGroup.getChildAt(j);
	                    if (b.isChecked()) {
	                       sr.post_data.put(key,values.getJSONObject(j).getString(Open311.KEY));
	                    }
	                }
				siCount++; 
			}
			else if (mDatatype.equals(Open311.STRING) || mDatatype.equals(Open311.NUMBER) || mDatatype.equals(Open311.TEXT)) {
			    	 
			    	 View desView = desViews.get(desCount); 
		            
			    	 EditText attribute_input = (EditText) desView.findViewById(R.id.input1);
			    	 sr.post_data.put(key, attribute_input.getText().toString());
			    	 					    	 
			    	 desCount++;
		            
			}
			else if(mDatatype.equals(Open311.MULTIVALUELIST)){
				
			  JSONArray submittedValues = new JSONArray();
              int count = values.length();
              
              for (int i=miCount; i < count; i++) {
                  CheckBox checkbox = miCheckBox.get(i);
                  if (checkbox.isChecked()) {
                      submittedValues.put(values.getJSONObject(i).getString(Open311.KEY));
                  }
              }
              sr.post_data.put(key, submittedValues.toString());
             miCount=count+1;
             
			}
		
		} 
		}catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
     }
}
  	
	/**
	 * Task for using Google's Geocoder
	 * 
	 * Queries Google's geocode, updates the address in ServiceRequest,
	 * then refreshes the view so the user can see the change
	 */
	private class ReverseGeocodingTask extends AsyncTask<LatLng, Void, String> {
	    @Override
	    protected String doInBackground(LatLng... params) {
	        Geocoder geocoder = new Geocoder(getActivity(), Locale.getDefault());
	        LatLng point = params[0];
	        double latitude  = point.latitude;
	        double longitude = point.longitude;

	        List<Address> addresses = null;
	        try {
	            addresses = geocoder.getFromLocation(latitude, longitude, 1);
	        } catch (IOException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	        }
	        
	        if (addresses != null && addresses.size() > 0) {
	            Address address = addresses.get(0);
	            return String.format("%s", address.getMaxAddressLineIndex() > 0 ? address.getAddressLine(0) : "");
	        }
	        return null;
	    }
	    
	    @Override
	    protected void onPostExecute(String address) {
	        if (address != null) {
	            try {
	                sr.post_data.put(Open311.ADDRESS_STRING, address);
	                
	            } catch (JSONException e) {
	                // TODO Auto-generated catch block
	                e.printStackTrace();
	            }
	        }
	        super.onPostExecute(address);
	    }
	}
	
	/**
	 * A basic date picker used for DateTime attributes
	 * 
	 * Pass in the attribute code that you want the user to enter a date for
	 */
	@SuppressLint("ValidFragment")
   private class DatePickerDialogFragment extends SherlockDialogFragment implements OnDateSetListener {
	    private String mAttributeCode;
	    
	    /**
	     * @param code The attribute code to update in mServiceRequest
	     */
	    public DatePickerDialogFragment(String code) {
	        mAttributeCode = code;
	    }
	    
	    @Override
	    public Dialog onCreateDialog(Bundle savedInstanceState) {
	        Calendar c = Calendar.getInstance();
	        return new DatePickerDialog(getActivity(), this, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
	    }

	    @Override
	    public void onDateSet(android.widget.DatePicker view, int year, int monthOfYear, int dayOfMonth) {
	        Calendar c = Calendar.getInstance();
	        c.set(year, monthOfYear, dayOfMonth);
	        try {
	            String code = String.format("%s[%s]", "attribute", mAttributeCode);
	            String date = DateFormat.getDateFormat(getActivity()).format(c.getTime());
                sr.post_data.put(code, date);
               
           } catch (JSONException e) {
               // TODO Auto-generated catch block
               e.printStackTrace();
           }
	    }
	}
	
	/***
	 * Loop through all the attributes and according to their DataType inflate the corresponding
	 * layout and add it to the parent layout. Also make sure they inflate one below the other. 
	 * @param parentLayout
	 * @param childLayout
	 */
	private void loadAttributeEntryView(RelativeLayout parentLayout,LinearLayout childLayout){
		
		LayoutInflater inflater = LayoutInflater.from(context);
		try {
			attributes = sr.service_definition.getJSONArray(Open311.ATTRIBUTES);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for(int k=0,count=2;k < attributes.length();k++,count++){
				
			//params should be same for each attribute layout
			RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
			
	        try {
	        	attribute  = attributes.getJSONObject(k);
	        	mVariable  = attribute.getBoolean(Open311.VARIABLE);
				mCode      = attribute.getString(Open311.CODE);
				mDatatype  = attribute.optString(Open311.DATATYPE, Open311.STRING);
				mDescription = attribute.getString(Open311.DESCRIPTION);
				
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        
	        //If attribute's variable is false then it is a header.
	        if(mVariable){
	        
	        if (mDatatype.equals(Open311.STRING) || mDatatype.equals(Open311.NUMBER) || mDatatype.equals(Open311.TEXT)) {
	        	
	            View v = inflater.inflate(R.layout.attribute_entry_string, null);
	            
	            desViews.add(v);
	            
	            LinearLayout childLayout1 = (LinearLayout) v.findViewById(R.id.childlayout1);
	                        
	            params.addRule(RelativeLayout.BELOW,childLayout.getId());
	            
	            childLayout1.setId(count);
	            
	            childLayout = childLayout1;
	            
	            EditText attribute_input = (EditText) v.findViewById(R.id.input1);
	            TextView title = (TextView) v.findViewById(R.id.title1);
	            title.setText(mDescription);
	            
	            if (mDatatype.equals(Open311.NUMBER)) {
	                attribute_input.setInputType(InputType.TYPE_CLASS_NUMBER);
	            }
	            if (mDatatype.equals(Open311.TEXT)) {
	                attribute_input.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE);
	            }
	            
	            childLayout1.setLayoutParams(params);
	            parentLayout.addView(childLayout1);
	        }
	        else if (mDatatype.equals(Open311.SINGLEVALUELIST) || mDatatype.equals(Open311.MULTIVALUELIST)) {
	            /**
	             * Each value object has a key and a name: {key:"", name:""}
	             * We want to display the name to the user, but need to POST
	             * the key to the endpoint.
	             * 
	             * We rely on the order to keep track of which value is which
	             */
	            JSONArray values = attribute.optJSONArray(Open311.VALUES);
	            int len = values.length();

	            if (mDatatype.equals(Open311.SINGLEVALUELIST)) {
	            	
	                View v = inflater.inflate(R.layout.attribute_entry_singlevaluelist, null);
	                
	                siViews.add(v);
	                
	                LinearLayout childLayout2 = (LinearLayout) v.findViewById(R.id.childlayout2);
	                               
	                params.addRule(RelativeLayout.BELOW,childLayout.getId());
	                
	                childLayout2.setId(count);
	                
	                childLayout = childLayout2;
	                
	                TextView title = (TextView) v.findViewById(R.id.title2);
	                title.setText(mDescription);
	                
	                RadioGroup siRadioGroup = (RadioGroup) v.findViewById(R.id.input2);
	                for (int i=0; i<len; i++) {
	                    JSONObject value = values.optJSONObject(i);
	                    RadioButton button = (RadioButton) inflater.inflate(R.layout.radiobutton, null);
	                    button.setText(value.optString(Open311.NAME));
	                    siRadioGroup.addView(button);
	                }
	                childLayout2.setLayoutParams(params);
	                parentLayout.addView(childLayout2);
	            }
	            else if (mDatatype.equals(Open311.MULTIVALUELIST)) {
	            	
	                View v = inflater.inflate(R.layout.attribute_entry_multivaluelist, null);
	                
	                LinearLayout childLayout3 = (LinearLayout) v.findViewById(R.id.childlayout3);
	                                                              
	                params.addRule(RelativeLayout.BELOW,childLayout.getId());
	                
	                childLayout3.setId(count);
	                
	                childLayout = childLayout3;
	                
	                TextView title = (TextView) v.findViewById(R.id.title3);
	                title.setText(mDescription);
	                
	                for (int i=0; i<len; i++) {
	                    JSONObject value = values.optJSONObject(i);
	                    CheckBox checkbox = (CheckBox) inflater.inflate(R.layout.checkbox, null);
	                    miCheckBox.add(checkbox);
	                    checkbox.setText(value.optString(Open311.NAME));
	                    childLayout3.addView(checkbox);
	               }
	                
	                childLayout3.setLayoutParams(params);
	                parentLayout.addView(childLayout3);
	            }
		}
	        else if(mDatatype.equals(Open311.DATETIME)) {
	        	
	        	View v = inflater.inflate(R.layout.list_item_other, null);
	            
	            LinearLayout childLayout4 = (LinearLayout) v.findViewById(R.id.attribute_layout);
	                            
	            params.addRule(RelativeLayout.BELOW,childLayout.getId());
	            
	            childLayout4.setId(count);
	            
	            childLayout = childLayout4;
	            
	            TextView prompt = (TextView) v.findViewById(R.id.prompt);
	            prompt.setText(sr.getAttributeDescription(mCode));
	        }
	         
		}else{
			
			View v = inflater.inflate(R.layout.list_item_header, null);
			LinearLayout headerLayout = (LinearLayout) v.findViewById(R.id.headerLayout);
		
			params.addRule(RelativeLayout.BELOW,childLayout.getId());
	        
	        headerLayout.setId(count);
	        
	        childLayout = headerLayout;
					
			TextView header = (TextView) v.findViewById(R.id.header);
			header.setText(mCode);
			
			headerLayout.setLayoutParams(params);
			parentLayout.addView(headerLayout);
		}
	}
	     

	}
	
	/**
	 * AsyncTask for sending the ServiceRequest to the endpoint
	 * 
	 * When finished the user will be sent to the Saved Reports screen
	 */
	private class PostServiceRequestTask extends AsyncTask<Void, Void, Boolean> {
	    private ProgressDialog mDialog;
	    private String mMediaPath;
	    private String errorMessage;
	    
	    @Override
	    protected void onPreExecute() {
            super.onPreExecute();
            mDialog = ProgressDialog.show(getActivity(), getString(R.string.dialog_posting_service), "", true);
            
            // Converting from a Uri to a real file path requires a database
            // cursor. Media.getRealPathFromUri must be done on the main UI
            // thread, since it makes its own loadInBackground call.
            if (sr.post_data.has(Open311.MEDIA)) {
                try {
                    mMediaPath = Media.getRealPathFromUri(Uri.parse(sr.post_data.getString(Open311.MEDIA)), getActivity());
                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
	    }
	    
        @Override
        protected Boolean doInBackground(Void... params) {
            JSONArray response;
            try {
                response = Open311.postServiceRequest(sr, getActivity(), mMediaPath);
                
                if (response.length() > 0) {
                    SimpleDateFormat isoDate = new SimpleDateFormat(Open311.DATETIME_FORMAT);
                    String requested_datetime = isoDate.format(new Date());
                    try {
                        sr.endpoint        = Open311.sEndpoint;
                        sr.service_request = response.getJSONObject(0);
                        sr.post_data.put(ServiceRequest.REQUESTED_DATETIME, requested_datetime);
                        return Open311.saveServiceRequest(getActivity(), sr);
                    } catch (JSONException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
            catch (ClientProtocolException e1) {
                errorMessage = getResources().getString(R.string.failure_posting_service);
            }
            catch (JSONException e1) {
                errorMessage = getResources().getString(R.string.failure_posting_service);
            }
            catch (IOException e1) {
                errorMessage = getResources().getString(R.string.failure_posting_service);
            }
            catch (Open311Exception e1) {
                errorMessage = e1.getDialogMessage();
            }
            return false;
        }
	    
        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            mDialog.dismiss();
            if (!result) {
                if (errorMessage == null) {
                    errorMessage = getString(R.string.failure_posting_service);
                }
                Util.displayCrashDialog(getActivity(), errorMessage);
            }
            else {
                Intent intent = new Intent(getActivity(), SavedReportsActivity.class);
                startActivity(intent);
            }
        }
	}
}
	