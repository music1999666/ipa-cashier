package com.ipaloma.jxbpay;

import android.content.Context;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.util.Log;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.security.KeyStore;
import java.util.Iterator;


public class PaymentStatusTask extends AsyncTask<Void, Void, String> {

    private static final String TAG = "PaymentStatusTask";
    private final String mUrl;
    private final long mTimeout;
    private final PaymentStatusResponse mDelegate;
    private final Context mContext;
    private PaymentStatusTask mInstance;
    private CountDownTimer mCountDown;
    private String mResult = "{\"error\":\"网络开小差了\"}";

    public interface PaymentStatusResponse {
        void onPaymentStatus(String output);
        void beforePaymentStatus(String url);
        void onPayementStatustTick(long millisUntilFinished);
    }

    public PaymentStatusTask(String url, long timeoutMillion, Context context, PaymentStatusResponse response){
        mUrl = url;
        mInstance = this;
        mTimeout = timeoutMillion;
        mDelegate = response;
        mContext = context;
    }
    @Override
    protected void onPreExecute(){
        mCountDown = new CountDownTimer(mTimeout, 1000) {
            public void onTick(long millisUntilFinished) {
                // You can monitor the progress here as well by changing the onTick() time
                Log.d(TAG, "seconds remaining: " + millisUntilFinished / 1000);
                if(mDelegate != null)
                    mDelegate.onPayementStatustTick(millisUntilFinished);
            }
            public void onFinish() {
                Log.d(TAG, "onFinish called");
                // stop async task if not in progress
                if (mInstance.getStatus() == AsyncTask.Status.RUNNING  || mInstance.getStatus() == AsyncTask.Status.PENDING) {
                    mInstance.cancel(true);
                    mDelegate.onPaymentStatus(mResult);
                }
            }

        }.start();
        if(mDelegate != null) {
            mDelegate.beforePaymentStatus(mUrl);
            mDelegate.onPayementStatustTick(mTimeout);  // 倒计时
        }
    }


    @Override
    protected void onCancelled() {
        mCountDown.cancel();
        super.onCancelled();
    }

    @Override
    protected void onPostExecute(final String result) {
        mCountDown.cancel();
        new Runnable() {
            @Override
            public void run() {
                if(mDelegate !=null)
                    mDelegate.onPaymentStatus(result);
            }
        }.run();
    }

    @Override
    protected String doInBackground(Void... voids) {
        JSONObject result;
        String status = "等待";

        while(!isCancelled()) {

            try {
                byte[] buf = httpGet(mUrl);
                if (buf == null || buf.length == 0)
                    return null;
                // 保存最后的返回结果，在倒计时结束的时候返回
                mResult = new String(buf);

                Log.d(TAG, mResult);

                result = new JSONObject(mResult);

                // 服务器明确返回错误，结束
                if(!result.optString("error","").equals(""))
                    return mResult;

                status = result.optString("sessionstatus", "");	// 等待|成功|关闭|退款
                Log.d(TAG, "get status : " + status);

            } catch (Exception e) {
                e.printStackTrace();
                    mResult = "{\"error\":\"网络开小差了\"}";    // 继续重试
            }

            try {

                // 如果没有获得结果，或者结果为等待，继续查询
                if ( !status.equals("等待")) return mResult;

                Thread.sleep(2000);
            }catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
        return mResult;
    }

    public static byte[] httpGet(String url) {
        if (url == null || url.length() == 0)
            return null;

        HttpClient httpClient = createHttpClient();
        HttpGet httpGet = new HttpGet(url);

        try {
            HttpResponse response;
            if ((response = httpClient.execute(httpGet)).getStatusLine().getStatusCode() != 200) {
                Log.e(TAG, "httpGet fail, status code = " + response.getStatusLine().getStatusCode());
                return null;
            }
            return EntityUtils.toByteArray(response.getEntity());

        } catch (Exception e) {
            Log.e(TAG, "httpGet exception, e = " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static HttpClient createHttpClient() {
        try {
            KeyStore keyStore;
            (keyStore = KeyStore.getInstance(KeyStore.getDefaultType())).load((InputStream) null, (char[]) null);
            //SocketFactory socketFactory4;
            //(socketFactory4 = new SocketFactory(keyStore)).setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            BasicHttpParams httpParams;
            HttpProtocolParams.setVersion(httpParams = new BasicHttpParams(), HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(httpParams, "UTF-8");
            SchemeRegistry registry;
            (registry = new SchemeRegistry()).register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            //registry.register(new Scheme("https", socketFactory4, 443));
            ThreadSafeClientConnManager var5 = new ThreadSafeClientConnManager(httpParams, registry);
            return new DefaultHttpClient(var5, httpParams);
        } catch (Exception var3) {
            return new DefaultHttpClient();
        }
    }
}