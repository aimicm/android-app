package wave.caribe.dashboard.services;

import android.os.AsyncTask;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Caribe Wave Android App
 *
 * Helper class for Http Async tasks
 *
 * Created by tchap on 14/03/16.
 */
public class AsyncHttpTask extends AsyncTask<String, Void, Integer> {

    private static final String TAG = "CW:ASYNC HTTP TASK";

    private JSONArray sensors = null;

    public interface TaskListener {
        void onFinished(JSONArray result);
    }

    // This is the reference to the associated listener
    private final TaskListener taskListener;

    public AsyncHttpTask(TaskListener listener) {
        // The listener reference is passed in through the constructor
        this.taskListener = listener;
    }


    @Override
    protected Integer doInBackground(String ... params) {
        InputStream inputStream;
        HttpURLConnection urlConnection;
        Integer result = 0;
        try {
            URL url = new URL(params[0]);
            urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setRequestMethod("GET");
            int statusCode = urlConnection.getResponseCode();

            /* 200 represents HTTP OK */
            if (statusCode ==  200) {
                inputStream = new BufferedInputStream(urlConnection.getInputStream());
                String response = convertInputStreamToString(inputStream);
                parseResult(response);
                result = 1;
            } else {
                result = 0;
            }
        } catch (Exception e) {
            Log.d(TAG, e.getLocalizedMessage());
        }
        return result;
    }

    @Override
    protected void onPostExecute(Integer result) {
        /* Download complete. Lets call mainactivity */
        if (result == 1){
            super.onPostExecute(result);

            // In onPostExecute we check if the listener is valid
            if(this.taskListener != null) {
                // And if it is we call the callback function on it.
                this.taskListener.onFinished(sensors);
            }
        } else {
            Log.e(TAG, "Failed to fetch data!");
        }
    }

    private String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        String line;
        String result = "";
        while((line = bufferedReader.readLine()) != null){
            result += line;
        }

        /* Close Stream */
        //noinspection ConstantConditions
        if(null!=inputStream){
            inputStream.close();
        }
        return result;
    }

    private void parseResult(String result) {
        try{
            sensors = new JSONArray(result);
        }catch (JSONException e){
            e.printStackTrace();
        }
    }
}