package com.ipaloma.jxbpay;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.util.Log;

import com.chinaums.pppay.unify.SocketFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.InputStream;
import java.security.KeyStore;


public class GetPayRequestTask extends AsyncTask<Void, Void, String> {
    private static final String TAG = "GetPayRequestTask";
    private  int mCountDownInterval = 8*1000;
    private final JSONObject parameter;
    private final Context mContext;
    private final GetPayRequestTask mInstance;
    private GetPayRequestResponse mDelegate = null;
    private CountDownTimer mCountDown;

    public interface GetPayRequestResponse {
        void onPayRequest(String output);
        void beforePayRequest(JSONObject param);
        void onPayRequestTick(long millisUntilFinished);
    }

    public GetPayRequestTask(JSONObject param, Context context, GetPayRequestResponse response) {
        parameter = param;
        mContext = context;
        mDelegate = response;
        mInstance = this;
    }

    @Override
    protected void onPreExecute() {
        mCountDown = new CountDownTimer(mCountDownInterval, 1000) {
            public void onTick(long millisUntilFinished) {
                // You can monitor the progress here as well by changing the onTick() time
                Log.d(TAG, "seconds remaining: " + millisUntilFinished / 1000);
                if(mDelegate != null && mCountDownInterval - millisUntilFinished > 1000)
                    mDelegate.onPayRequestTick(mCountDownInterval - millisUntilFinished);   // 正向
            }
            public void onFinish() {
                Log.d(TAG, "onFinish called");
                // stop async task if not in progress
                if (mInstance.getStatus() == AsyncTask.Status.RUNNING  || mInstance.getStatus() == AsyncTask.Status.PENDING) {
                    mInstance.cancel(true);
                    // Add any specific task you wish to do as your extended class variable works here as well.
                    if(mDelegate != null){
                        mDelegate.onPayRequest("{\"error\":\"网络开小差了\"}");
                    }
                }
            }
        }.start();
        if(mDelegate != null) {
            mDelegate.beforePayRequest(parameter);
        }
    }

    @Override
    protected void onPostExecute(final String result) {

        mCountDown.cancel();
        new Runnable() {
            @Override
            public void run() {
                if(mDelegate !=null)
                    mDelegate.onPayRequest(result);
            }
        }.run();
    }

    @Override
    protected void onCancelled() {
        mCountDown.cancel();
        super.onCancelled();
    }

    @Override
    protected String doInBackground(Void... params) {
        String env = parameter.optString("env", "");
        env = env == null ? "" : env;
        String url = (env.equalsIgnoreCase("dev") ? mContext.getString(R.string.dev_env_url)
                : env.equalsIgnoreCase("demo") ? mContext.getString(R.string.demo_env_url)
                : mContext.getString(R.string.online_env_url))
                + parameter.optString("method");

        String entity = "";

        entity = parameter.toString();

        Log.d(TAG, "doInBackground, url = " + url);
        Log.d(TAG, "doInBackground, entity = " + entity);

        // TODO 根据传入参数：订单编号/订单金额，沙盒/根据用户选择支付方式
        //  传递到服务器，从服务器获取相应参数，并通过服务器向银联商务请求支付订单
        //  回传处理结果，再发起app支付
        byte[] buf = httpPost(url, entity);
        if (buf == null || buf.length == 0) {
            return null;
        }

        String content = new String(buf);
        Log.d(TAG, "doInBackground, content = " + content);
        return content;
    }

    public static byte[] httpPost(String url, String param) {
        if (url != null && url.length() != 0) {
            HttpClient httpClient = createHttpClient();
            HttpPost httpPost = new HttpPost(url);

            try {
                httpPost.setEntity(new StringEntity(param, "utf-8"));
                httpPost.setHeader("Content-Type", "application/json;charset=UTF-8");
                HttpResponse response;
                if ((response = httpClient.execute(httpPost)).getStatusLine().getStatusCode() != 200) {
                    Log.e("SDK_Sample.Util", "httpGet fail, status code = " + response.getStatusLine().getStatusCode());
                }
                HttpEntity result = response.getEntity();
                if(result == null)
                    return null;
                return EntityUtils.toByteArray(result);
            } catch (Exception var3) {
                Log.e("SDK_Sample.Util", "httpPost exception, e = " + var3.getMessage());
                var3.printStackTrace();
                return null;
            }
        } else {
            Log.e("SDK_Sample.Util", "httpPost, url is null");
            return null;
        }
    }

    private static HttpClient createHttpClient() {
        try {
            KeyStore keyStore;
            (keyStore = KeyStore.getInstance(KeyStore.getDefaultType())).load((InputStream) null, (char[]) null);
            SocketFactory socketFactory4;
            (socketFactory4 = new SocketFactory(keyStore)).setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            BasicHttpParams httpParams;
            HttpProtocolParams.setVersion(httpParams = new BasicHttpParams(), HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(httpParams, "UTF-8");
            SchemeRegistry registry;
            (registry = new SchemeRegistry()).register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", socketFactory4, 443));
            ThreadSafeClientConnManager var5 = new ThreadSafeClientConnManager(httpParams, registry);
            return new DefaultHttpClient(var5, httpParams);
        } catch (Exception var3) {
            return new DefaultHttpClient();
        }
    }
}
