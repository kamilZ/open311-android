/**
 * @copyright 2012 City of Bloomington, Indiana
 * @license http://www.gnu.org/licenses/gpl.txt GNU/GPL, see LICENSE.txt
 * @author Cliff Ingham <inghamn@bloomington.in.gov>
 */

package gov.in.bloomington.georeporter.models;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.os.Build;
import android.util.Log;

import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.HttpStatus;
import ch.boye.httpclientandroidlib.HttpVersion;
import ch.boye.httpclientandroidlib.NameValuePair;
import ch.boye.httpclientandroidlib.client.ClientProtocolException;
import ch.boye.httpclientandroidlib.client.entity.UrlEncodedFormEntity;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.conn.scheme.PlainSocketFactory;
import ch.boye.httpclientandroidlib.conn.scheme.Scheme;
import ch.boye.httpclientandroidlib.entity.mime.MultipartEntity;
import ch.boye.httpclientandroidlib.entity.mime.content.ByteArrayBody;
import ch.boye.httpclientandroidlib.entity.mime.content.StringBody;
import ch.boye.httpclientandroidlib.impl.client.DefaultHttpClient;
import ch.boye.httpclientandroidlib.message.BasicNameValuePair;
import ch.boye.httpclientandroidlib.params.CoreConnectionPNames;
import ch.boye.httpclientandroidlib.params.CoreProtocolPNames;
import ch.boye.httpclientandroidlib.util.EntityUtils;

import com.android.volley.RequestQueue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import gov.in.bloomington.georeporter.R;
import gov.in.bloomington.georeporter.json.RequestResponseJson;
import gov.in.bloomington.georeporter.json.ServerAttributeJson;
import gov.in.bloomington.georeporter.json.ServiceDefinationJson;
import gov.in.bloomington.georeporter.json.ServiceEntityJson;
import gov.in.bloomington.georeporter.util.EasySSLSocketFactory;
import gov.in.bloomington.georeporter.util.Media;
import gov.in.bloomington.georeporter.util.Open311Parser;
import gov.in.bloomington.georeporter.util.json.JSONArray;
import gov.in.bloomington.georeporter.util.json.JSONException;
import gov.in.bloomington.georeporter.util.json.JSONObject;
import gov.in.bloomington.georeporter.volleyrequests.GsonGetRequest;
import gov.in.bloomington.georeporter.volleyrequests.GsonPostServiceRequest;
import gov.in.bloomington.georeporter.volleyrequests.Open311XmlRequest;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class Open311 {
    /**
     * Constants for Open311 keys I'm tired of making typos in key names
     */
    // Global required fields
    public static final String JURISDICTION = "jurisdiction_id";
    public static final String API_KEY = "api_key";
    public static final String FORMAT = "format";
    public static final String SERVICE_CODE = "service_code";
    public static final String SERVICE_NAME = "service_name";
    public static final String GROUP = "group";
    // Global basic fields
    public static final String MEDIA = "media";
    public static final String MEDIA_URL = "media_url";
    public static final String LATITUDE = "lat";
    public static final String LONGITUDE = "long";
    public static final String ADDRESS = "address";
    public static final String ADDRESS_STRING = "address_string";
    public static final String DESCRIPTION = "description";
    public static final String SERVICE_NOTICE = "service_notice";
    public static final String ACCOUNT_ID = "account_id";
    public static final String STATUS = "status";
    public static final String STATUS_NOTES = "status_notes";
    public static final String AGENCY_RESPONSIBLE = "agency_responsible";
    public static final String REQUESTED_DATETIME = "requested_datetime";
    public static final String UPDATED_DATETIME = "updated_datetime";
    public static final String EXPECTED_DATETIME = "expected_datetime";
    // Personal Information fields
    public static final String EMAIL = "email";
    public static final String DEVICE_ID = "device_id";
    public static final String FIRST_NAME = "first_name";
    public static final String LAST_NAME = "last_name";
    public static final String PHONE = "phone";
    // Custom field definition in service_definition
    public static final String METADATA = "metadata";
    public static final String ATTRIBUTES = "attributes";
    public static final String VARIABLE = "variable";
    public static final String CODE = "code";
    public static final String ORDER = "order";
    public static final String VALUES = "values";
    public static final String VALUE = "value";
    public static final String KEY = "key";
    public static final String NAME = "name";
    public static final String REQUIRED = "required";
    public static final String DATATYPE = "datatype";
    public static final String STRING = "string";
    public static final String NUMBER = "number";
    public static final String DATETIME = "datetime";
    public static final String TEXT = "text";
    public static final String TRUE = "true";
    public static final String SINGLEVALUELIST = "singlevaluelist";
    public static final String MULTIVALUELIST = "multivaluelist";
    // Key names from /res/raw/available_servers.json
    public static final String URL = "url";
    public static final String SUPPORTS_MEDIA = "supports_media";
    // Key names for the saved reports file
    private static final String SAVED_REPORTS_FILE = "service_requests";
    public static final String SERVICE_REQUEST_ID = "service_request_id";
    public static final String TOKEN = "token";
    // Key names for formats
    public static final String JSON = "json";
    public static final String XML = "xml";
    // Key name for encoding
    public static final String UTF_8 = "UTF-8";

    public static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";

    public static ServerAttributeJson sEndpoint;
    public static boolean isLatestServiceListLoaded = false;
    public static boolean isDataLoading = false;
    public static String prevEndpoint;
    public static HashMap<String, ServiceDefinationJson> sServiceDefinitions;
    public static ArrayList<String> sGroups;

    public static RequestQueue requestQueue = null;
    public static GsonGetRequest<ArrayList<ServiceEntityJson>> sServiceRequestGson = null;
    public static Open311XmlRequest<ArrayList<ServiceEntityJson>> sServiceRequestXML = null;
    public static GsonPostServiceRequest sPostServiceRequest = null;

    public static ArrayList<ServiceEntityJson> sServiceList = null;

    public static String mBaseUrl;
    public static String mJurisdiction;
    public static String mApiKey;
    public static String mFormat = "json";

    private static DefaultHttpClient mClient = null;
    private static final int TIMEOUT = 3000;

    private static Open311 mInstance;

    private Open311() {
    }

    public static synchronized Open311 getInstance() {
        if (mInstance == null) {
            mInstance = new Open311();
        }
        return mInstance;
    }

    /**
     * Lazy load an Http client
     * 
     * @return DefaultHttpClient
     */
    public static DefaultHttpClient getClient(Context c) {
        if (mClient == null) {
            mClient = new DefaultHttpClient();

            String user_agent;
            try {
                PackageInfo info = c.getPackageManager().getPackageInfo(
                        c.getPackageName(), 0);
                user_agent = String.format("%s/%s (Android/%s)",
                        c.getString(R.string.app_name), info.versionName,
                        Build.VERSION.RELEASE);
            } catch (NameNotFoundException e) {
                user_agent = String.format("%s (Android/%s)",
                        c.getString(R.string.app_name), Build.VERSION.RELEASE);
            }

            Scheme http = new Scheme("http", 80,
                    PlainSocketFactory.getSocketFactory());
            Scheme https = new Scheme("https", 443, new EasySSLSocketFactory());
            mClient.getConnectionManager().getSchemeRegistry().register(http);
            mClient.getConnectionManager().getSchemeRegistry().register(https);

            mClient.getParams().setParameter(
                    CoreProtocolPNames.HTTP_CONTENT_CHARSET, "UTF-8");
            mClient.getParams().setParameter(
                    CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
            mClient.getParams().setParameter(CoreProtocolPNames.USER_AGENT,
                    user_agent);
            mClient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT,
                    TIMEOUT);
            mClient.getParams().setParameter(
                    CoreConnectionPNames.CONNECTION_TIMEOUT, TIMEOUT);
        }
        return mClient;
    }

    // TODO Doc
    public static void setCurrentServerDetails(ServerAttributeJson cuurentServer)
    {
        mBaseUrl = cuurentServer.url;
        mJurisdiction = cuurentServer.jurisdiction_id;
        mApiKey = cuurentServer.api_key;
        mFormat = cuurentServer.format;
    }

    /**
     * Loads all the service information from the endpoint Endpoints will have a
     * service_list, plus, for each service, there may be a service_definition.
     * To make the user experience smoother, we are downloading and saving all
     * the possible service information at once. Returns false if there was a
     * problem
     * 
     * @param current_server A single entry from /raw/available_servers
     * @return Boolean
     */
    /*
     * public static Boolean setEndpoint(ServerAttributeJson current_server,
     * Context context) { sReady = false; mBaseUrl = null; mJurisdiction = null;
     * mApiKey = null; mFormat = null; sGroups = new ArrayList<String>();
     * sServiceList = null; sServiceDefinitions = new HashMap<String,
     * ServiceDefinationJson>(); try { //Open311Parser mParser = new
     * Open311Parser(mFormat); // TODO if(mFormat.contentEquals(Open311.JSON)) {
     * ArrayList<ServiceDefinationJson> a = null; sServiceRequest = new
     * GsonGetRequest<ArrayList<ServiceEntityJson>>(getServiceListUrl(), new
     * TypeToken<ArrayList<ServiceEntityJson>>() { }.getType(), null, new
     * Listener<ArrayList<ServiceEntityJson>>() {
     * @Override public void onResponse(ArrayList<ServiceEntityJson> response) {
     * sServiceList = response; } }, new ErrorListener() {
     * @Override public void onErrorResponse(VolleyError error) { } }); } // Go
     * through all the services and pull out the seperate groups // Also, while
     * we're running through, load any service_definitions String group = "";
     * int len = sServiceList.size(); for (int i = 0; i < len; i++) {
     * ServiceEntityJson s = sServiceList.get(i); // services may have an empty
     * string for the group parameter group = s.getGroup(); if
     * (group.equals("")) { group = context.getString(R.string.uncategorized); }
     * if (!sGroups.contains(group)) { sGroups.add(group); } // Add Service
     * Definitions to mServiceDefinitions if (s.getMetadata() == true) { String
     * code = s.getService_code(); //TODO ServiceDefinationJson definition =
     * getServiceDefinition(code, context); if (definition != null) {
     * sServiceDefinitions.put(code, definition); } } } } catch (Exception e) {
     * Log.d("Ready", sReady.toString()); e.printStackTrace(); return false; }
     * sEndpoint = current_server; sReady = true; return sReady; }
     */

    /**
     * Returns the services for a given group
     * 
     * @param group
     * @return ArrayList<ServiceEntityJson>
     */
    public static ArrayList<ServiceEntityJson> getServices(String group,
            Context context) {
        ArrayList<ServiceEntityJson> services = new ArrayList<ServiceEntityJson>();
        int len = sServiceList.size();
        for (int i = 0; i < len; i++) {

            ServiceEntityJson s = sServiceList.get(i);
            if (group.equals(context.getString(R.string.uncategorized))) {
                services.add(s);
                continue;
            }
            if (s.getGroup().equals(group)) {
                services.add(s);
            }

        }
        return services;
    }

    /**
     * @param service_code
     * @return ServiceDefinationJson
     */
    public static ServiceDefinationJson getServiceDefinition(String service_code, Context context) {

        if (sServiceDefinitions.containsKey(service_code)) {
            return sServiceDefinitions.get(service_code);
        }
        // TODO
        return null;
    }

    /**
     * POST new service request data to the endpoint The JSONObject should come
     * from ServiceRequest.post_data In the JSON data: All the keys should
     * already be named correctly. Attribute keys will already be in the form of
     * "attribute[code]". Most attributes will just contain single values
     * entered by the user; however, MultiValueList attributes will be an array
     * of the chosen values We will need to iterate over MultiValueList values
     * and add a seperate pair to the POST for each value. Media attributes will
     * contain the URI to the image file.
     * 
     * @param data JSON representation of user input
     * @return JSONObject
     * @throws JSONException
     * @throws IOException
     * @throws ClientProtocolException
     * @throws Open311Exception
     */

    public static ArrayList<RequestResponseJson> postServiceRequest(ServiceRequest sr,
            Context context, String mediaPath) throws JSONException,
            ClientProtocolException, IOException, Open311Exception {
        HttpPost request = new HttpPost(mBaseUrl + "/requests." + mFormat);
        ArrayList<RequestResponseJson> serviceRequests = null;
        if (mediaPath != null) {
            request.setEntity(prepareMultipartEntity(sr, context, mediaPath));
        } else {
            request.setEntity(prepareUrlEncodedEntity(sr));
        }
        HttpResponse r = getClient(context).execute(request);
        String responseString = EntityUtils.toString(r.getEntity());
        
        Log.d("Server Response", responseString);

        int status = r.getStatusLine().getStatusCode();
        // The spec does not declare what exact status codes to use
        // Bloomington uses 200 Okay
        // Chicago uses 201 Created
        if (status == HttpStatus.SC_OK || status == HttpStatus.SC_CREATED
                || status == HttpStatus.SC_ACCEPTED) {
            Open311Parser mParser = new Open311Parser(mFormat);

            // TODO
            serviceRequests = new Gson().fromJson(responseString,
                    new TypeToken<ArrayList<RequestResponseJson>>() {
                    }.getType());
            
            Log.d("Server Response Parsed","Yes");
        } else {
            // The server indicated some error. See if they returned the
            // error description as JSON
            String dialogMessage;
            try {
                Open311Parser mParser = new Open311Parser(mFormat);
                JSONArray errors = mParser.parseErrors(responseString);
                dialogMessage = errors.getJSONObject(0).getString(
                        Open311.DESCRIPTION);
            } catch (JSONException e) {
                switch (status) {
                    case 403:
                        dialogMessage = context.getResources().getString(
                                R.string.error_403);
                        break;
                    default:
                        dialogMessage = context.getResources().getString(
                                R.string.failure_posting_service);
                }
            }
            throw new Open311Exception(dialogMessage);
        }
        return serviceRequests;
    }

    public static String getServiceRequestId(String token) {
        return "";
    }

    /**
     * Prepares a POST that does not contain a media attachment
     * 
     * @param data
     * @return
     * @throws UnsupportedEncodingException UrlEncodedFormEntity
     * @throws JSONException
     */
    private static UrlEncodedFormEntity prepareUrlEncodedEntity(
            ServiceRequest sr) throws UnsupportedEncodingException,
            JSONException {
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        // This could cause a JSONException, but we let this one bubble up the
        // stack
        // If we don't have a service_code, we don't have a valid POST
        pairs.add(new BasicNameValuePair(SERVICE_CODE, sr.service
                .getService_code()));

        if (mJurisdiction.length() > 0) {
            pairs.add(new BasicNameValuePair(JURISDICTION, mJurisdiction));
        }
        if (mApiKey.length() > 0) {
            pairs.add(new BasicNameValuePair(API_KEY, mApiKey));
        }

        JSONObject data = sr.post_data;
        Iterator<?> keys = data.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            Object o;
            try {
                o = data.get(key);
                // Add MULTIVALUELIST values
                if (o instanceof JSONArray) {
                    String k = key + "[]"; // Key name to POST multiple values
                    JSONArray values = (JSONArray) o;
                    int len = values.length();
                    for (int i = 0; i < len; i++) {
                        try {
                            pairs.add(new BasicNameValuePair(k, values
                                    .getString(i)));
                        } catch (JSONException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
                // All other fields can just be plain key-value pairs
                else {
                    // Lat and Long need to be converted to string
                    if (o instanceof Double) {
                        o = Double.toString((Double) o);
                    }
                    pairs.add(new BasicNameValuePair(key, (String) o));
                }
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return new UrlEncodedFormEntity(pairs, UTF_8);
    }

    /**
     * Prepares a POST that includes a media attachment
     * 
     * @param data
     * @param context
     * @param mediaPath
     * @return
     * @throws UnsupportedEncodingException MultipartEntity
     * @throws JSONException
     */
    private static MultipartEntity prepareMultipartEntity(ServiceRequest sr,
            Context context, String mediaPath)
            throws UnsupportedEncodingException, JSONException {
        MultipartEntity post = new MultipartEntity();
        // This could cause a JSONException, but we let this one bubble up the
        // stack
        // If we don't have a service_code, we don't have a valid POST
        post.addPart(SERVICE_CODE,
                new StringBody(sr.service.getService_code()));

        if (mJurisdiction != null) {
            post.addPart(JURISDICTION, new StringBody(mJurisdiction));
        }
        if (mApiKey != null) {
            post.addPart(API_KEY, new StringBody(mApiKey));
        }
        JSONObject data = sr.post_data;
        Iterator<?> keys = data.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            Object o;
            try {
                o = data.get(key);
                // Attach media to the post
                // Do not read from the data object.
                // Instead, use the mediaPath that is passed in.
                // This relies on the fact that there can only be one media
                // attachment per ServiceRequest.
                if (key == MEDIA) {
                    final Bitmap media = Media.decodeSampledBitmap(mediaPath,
                            Media.UPLOAD_WIDTH, Media.UPLOAD_HEIGHT, context);
                    final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    media.compress(CompressFormat.PNG, 100, stream);
                    final byte[] binaryData = stream.toByteArray();
                    post.addPart(Open311.MEDIA, new ByteArrayBody(binaryData,
                            Media.UPLOAD_FILENAME));
                }
                // Attach MULTIVALUELIST values
                else if (o instanceof JSONArray) {
                    String k = key + "[]"; // Key name to POST multiple values
                    JSONArray values = (JSONArray) o;
                    int len = values.length();
                    for (int i = 0; i < len; i++) {
                        try {
                            post.addPart(k, new StringBody(values.getString(i)));
                        } catch (JSONException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
                // All other fields can be attached as plain key-value pairs
                else {
                    if (o instanceof Double) {
                        o = Double.toString((Double) o);
                    }
                    Charset charset = Charset.forName(UTF_8);
                    post.addPart(key, new StringBody((String) o, charset));
                }
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return post;
    }

    /**
     * Reads the saved reports file into a JSONArray Reports are stored as a
     * file on the device internal storage The file is a serialized JSONArray of
     * reports.
     * 
     * @return JSONArray
     */
    public static ArrayList<ServiceRequest> loadServiceRequests(Context c) {
        ArrayList<ServiceRequest> service_requests = null;
        
        GsonBuilder builder = new GsonBuilder();
        builder.excludeFieldsWithModifiers(Modifier.STATIC, Modifier.TRANSIENT);
        Gson gson = builder.create();
        
       
        byte[] bytes = new byte[1024];
        Log.d("Service Request Trying Loading", "Lets See");
        @SuppressWarnings("unused")
        int length;
        try {
            
            FileInputStream in = c.openFileInput(SAVED_REPORTS_FILE);
            InputStreamReader inputStreamReader = new InputStreamReader(in);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }
            
            Log.d("Service Request Loading", sb.toString());
            service_requests = gson.fromJson(sb.toString(),
                    new TypeToken<ArrayList<ServiceRequest>>() {
                    }.getType());
            in.close();
        } catch (FileNotFoundException e) {
            Log.w("Open311.loadServiceRequests",
                    "Saved Reports File does not exist");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            
        }
        return service_requests;
    }

    private static void closeQuietly(Closeable c) {
        if (c == null)
            return;
        try {
            c.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Writes the stored reports back out the file
     * 
     * @param c
     * @param requests An array of JSON-serialized ServiceRequest objects void
     */
    public static boolean saveServiceRequests(Context c, ArrayList<ServiceRequest> requests) {
        GsonBuilder builder = new GsonBuilder();
        builder.excludeFieldsWithModifiers(Modifier.STATIC, Modifier.TRANSIENT);
        Gson gson = builder.create();
        Log.d("Service Request Save trying", "Lets See");
        String json = gson.toJson(requests);
        Log.d("Service Request Save", json);
        FileOutputStream out;
        try {
            out = c.openFileOutput(SAVED_REPORTS_FILE, Context.MODE_PRIVATE);
            out.write(json.getBytes());
            out.close();
            return true;
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Adds a ServiceRequest to the collection of saved reports Reports are
     * stored as a file on the device internal storage The file is a serialized
     * JSONArray of ServiceRequest objects.
     * 
     * @param report
     * @return Boolean
     */
    public static boolean saveServiceRequest(Context c, ServiceRequest sr) {
        sr.endpoint = sEndpoint;

        ArrayList<ServiceRequest> saved_requests = loadServiceRequests(c);
        
        if(saved_requests == null)
            saved_requests = new ArrayList<ServiceRequest>();
        
        // Push the new report onto the start of the array
        saved_requests.add(0, sr);

        return saveServiceRequests(c, saved_requests);

    }

    /**
     * Returns the response content from an HTTP request
     * 
     * @param url
     * @return String
     */
    public static String loadStringFromUrl(String url, Context c)
            throws ClientProtocolException, IOException, IllegalStateException {
        HttpResponse r = getClient(c).execute(new HttpGet(url));
        String response = EntityUtils.toString(r.getEntity());

        return response;
    }

    /**
     * http://endpoint/services.format?jurisdiction_id=jurisdiction
     * 
     * @return String
     */
    public static String getServiceListUrl() {
        String url = mBaseUrl + "/services." + mFormat;
        if (mJurisdiction != null && mJurisdiction.length() > 0) {
            url = url + "?" + JURISDICTION + "=" + mJurisdiction;
        }
        return url;
    }

    /**
     * http://endpoint/services/service_code.format?jurisdiction_id=jurisdiction
     * 
     * @param service_code
     * @return String
     */
    public static String getServiceDefinitionUrl(String service_code) {
        String url = mBaseUrl + "/services/" + service_code + "." + mFormat;
        if (mJurisdiction.length() > 0) {
            url = url + "?" + JURISDICTION + "=" + mJurisdiction;
        }
        return url;
    }
}
