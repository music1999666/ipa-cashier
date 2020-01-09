package com.ipaloma.jxbpay;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.chinaums.pppay.unify.SocketFactory;
import com.chinaums.pppay.unify.UnifyPayListener;
import com.chinaums.pppay.unify.UnifyPayPlugin;
import com.chinaums.pppay.unify.UnifyPayRequest;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.unionpay.UPPayAssistEx;

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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity implements UnifyPayListener {
    private final static String TAG = "MainActivity";
    private TextView wxtype, alipay, cloudpay;

    private SharedPreferences mSharedPreferences;

    private Activity mActivity = null;

    String[] permissions = new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    List<String> mPermissionList = new ArrayList<>();
    private Intent mStartIntent;
    private String mResultUrl;
    private UnifyPayPlugin mUnifyPlugin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mStartIntent = getIntent();
        final JSONObject json = new JSONObject();
        try {
            json.putOpt("amount", mStartIntent.getDoubleExtra("amount", 0));
            json.putOpt("sandbox", mStartIntent.getStringExtra("sandbox"));
            json.putOpt("title", mStartIntent.getStringExtra("title"));
            json.putOpt("billnumber", mStartIntent.getStringExtra("billnumber"));
            json.putOpt("notifyurl", mStartIntent.getStringExtra("notifyurl"));
            json.putOpt("env", mStartIntent.getStringExtra("env"));

        }catch (Exception e){
            Log.e(TAG, "error parameters", e);
            e.printStackTrace();
        }

        if(mStartIntent.getExtras() != null)
            Log.d(TAG, "start parameter" + mStartIntent.getExtras().toString());
        setContentView(R.layout.activity_main);

        // title
        ((TextView) findViewById(R.id.head_title)).setText(json.optString("title", "") == "" ? "收银台" : json.optString("title"));

        //支付环境选择
        wxtype = (TextView) findViewById(R.id.weixin_pay);   // 1
        alipay = (TextView) findViewById(R.id.ali_pay); // 2
        cloudpay = (TextView) findViewById(R.id.cloud_quicki_pay); // 3

        wxtype.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new GetPrepayIdTask(1, json).execute();
            }
        });

        alipay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new GetPrepayIdTask(2, json).execute();
            }
        });

        cloudpay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new GetPrepayIdTask(3, json).execute();
            }
        });

        mUnifyPlugin = UnifyPayPlugin.getInstance(this);
        mUnifyPlugin.setListener(this);

        mActivity = this;
        //权限确认
        mPermissionList.clear();
        for (int i = 0; i < permissions.length; i++) {
            if (ContextCompat.checkSelfPermission(this, permissions[i]) != PackageManager.PERMISSION_GRANTED) {
                mPermissionList.add(permissions[i]);
            }
        }

        if (!mPermissionList.isEmpty()) {
            //请求权限方法
            String[] permissions = mPermissionList.toArray(new String[mPermissionList.size()]);//将List转为数组
            ActivityCompat.requestPermissions(this, permissions, 1);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mUnifyPlugin != null)
                mUnifyPlugin.clean();
        } catch (Exception e) {
            Log.e(TAG, "get exception when close MainActivity", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        // 判断是否勾选禁止后不再询问
                        boolean showRequestPermission = ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i]);
                        if (showRequestPermission) {//
                            //judgePermission();//重新申请权限
                            return;
                        } else {
                            finish();
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "---onActivityResult--- \n" + requestCode + " " + resultCode + " " + (data == null ? "data = null" : data.toString()));

        /**
         * 处理银联云闪付手机支付控件返回的支付结果
         */
        String msg = "";
        int result = RESULT_OK;
        if (data != null) {
            /*
             * 支付控件返回字符串:success、fail、cancel 分别代表支付成功，支付失败，支付取消
             */
            String str = data.getExtras().getString("pay_result");
            if (str.equalsIgnoreCase("success")) {
                //如果想对结果数据校验确认，直接去商户后台查询交易结果，
                //校验支付结果需要用到的参数有sign、data、mode(测试或生产)，sign和data可以在result_data获取到
                /**
                 * result_data参数说明：
                 * sign —— 签名后做Base64的数据
                 * data —— 用于签名的原始数据
                 *      data中原始数据结构：
                 *      pay_result —— 支付结果success，fail，cancel
                 *      tn —— 订单号
                 */
                msg = "云闪付支付成功";
            } else if (str.equalsIgnoreCase("fail")) {
                msg = "云闪付支付失败！";
                result = RESULT_CANCELED;
            } else if (str.equalsIgnoreCase("cancel")) {
                msg = "用户取消了云闪付支付";
                result = RESULT_CANCELED;
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
        // 被调用起来的，返回结果，并结束当前进程
        Log.d(TAG, "onActivityResult start close app");
        //mStartIntent.putExtra("message", msg);
        //mStartIntent.putExtra("resulturl", mResultUrl);
        //setResult(result, mStartIntent);
        Log.d(TAG, "onActivityResult close app done");
        //finish();
    }

    @Override
    public void onResult(String resultCode, String resultInfo) {
        Log.d(TAG, "onResult resultCode=" + resultCode + ", resultInfo=" + resultInfo);
        if (mStartIntent == null) return;

        Log.d(TAG, "onResult start close app");
        mStartIntent.putExtra("message", resultInfo);
        mStartIntent.putExtra("resulturl", mResultUrl);
        Log.d(TAG, "onResult close app done");

        //setResult(resultCode.equals("0000") || resultCode.equals("2001") ? RESULT_OK : RESULT_CANCELED, mStartIntent);
        //finish();
    }

    private class GetPrepayIdTask extends AsyncTask<Void, Void, String> {

        private final JSONObject parameter;
        private ProgressDialog dialog;
        private int typetag = 1;

        public GetPrepayIdTask(int type, JSONObject param) {
            typetag = type;parameter = param;
        }

        @Override
        protected void onPreExecute() {
            dialog =
                    ProgressDialog.show(MainActivity.this,
                            getString(R.string.app_tip),
                            getString(R.string.getting_prepayid));
        }

        @Override
        protected void onPostExecute(String result) {

            Log.i(TAG, "onPostExecute-->" + result);
            int retCode = RESULT_CANCELED;
            String retMessage="";
            String payRequest = "";
            JSONObject json;

            if (dialog != null) {
                dialog.dismiss();
            }
            try {

                json = new JSONObject(result == null ? "{\"error\":\"连接服务器失败\"}" : result);
                String error = json.optString("error", "").trim();
                payRequest = json.optString("app_pay_request", "").trim();
                mResultUrl = json.optString("result_url", "");  // 从服务器返回的状态查询 url
                if (json.has("result_url"))
                    json.remove("result_url");

                retCode = error.equals("") && !payRequest.equals("") && !mResultUrl.equals("") ? RESULT_OK : RESULT_CANCELED;
                retMessage = !error.equals("") ? String.format(getString(R.string.get_prepayid_fail), error)
                                        : payRequest.equals("") ? "服务器返回数据格式有问题，缺少“appPayRequest”字段"
                                        : getString(R.string.get_prepayid_succ);
            }catch (JSONException e){
                Log.e(TAG, "exception:", e);
                retMessage = "返回结果处理异常";
            }

            if(retCode == RESULT_OK){
                if (typetag == 0) {
                    payUMSPay(payRequest);
                } else if (typetag == 1) {
                    payWX(payRequest);
                } else if (typetag == 2) {
                    payAliPay(payRequest);
                } else if (typetag == 3) {
                    payCloudQuickPay(payRequest);
                }
                mStartIntent.putExtra("result_url", mResultUrl);
            }

            Log.d(TAG, retMessage);
            if(BuildConfig.DEBUG)
                Toast.makeText(MainActivity.this, retMessage, Toast.LENGTH_LONG).show();
            mStartIntent.putExtra("message", retMessage);
            mActivity.setResult(retCode, mStartIntent);
            mActivity.finish();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

        @Override
        protected String doInBackground(Void... params) {
            String env = parameter.optString("env", "");
            env = env == null ? "" : env;
            String url = env.equalsIgnoreCase("dev") ? getString(R.string.dev_env_url)
                                : env.equalsIgnoreCase("demo") ? getString(R.string.demo_env_url)
                                : getString(R.string.online_env_url);

            String entity = "";

            try {
                parameter.putOpt("pay_type",
                        typetag == 1 ? "微信" // getWeiXinParams
                                : typetag == 2 ? "支付宝" // getAliPayParm
                                : typetag == 3 ? "云闪付"
                                : "微信"); // getCloudQuickPayParm

                entity = parameter.toString();
            } catch (JSONException e) {
                Log.e(TAG, "exception in prepare parameter", e);
                return null;
            }

            Log.d(TAG, "typetag:" + typetag);
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
    }

    /**
     * 微信
     *
     * @param parms
     */
    private void payWX(String parms) {
        UnifyPayRequest msg = new UnifyPayRequest();
        msg.payChannel = UnifyPayRequest.CHANNEL_WEIXIN;
        msg.payData = parms;
        UnifyPayPlugin.getInstance(this).sendPayRequest(msg);
    }

    /**
     * 支付宝
     *
     * @param parms
     */
    private void payAliPay(String parms) {
        UnifyPayRequest msg = new UnifyPayRequest();
        msg.payChannel = UnifyPayRequest.CHANNEL_ALIPAY;
        msg.payData = parms;
        UnifyPayPlugin.getInstance(this).sendPayRequest(msg);
    }

    /**
     * 快捷支付
     *
     * @param parms
     */
    private void payUMSPay(String parms) {
        UnifyPayRequest msg = new UnifyPayRequest();
        msg.payChannel = UnifyPayRequest.CHANNEL_UMSPAY;
        msg.payData = parms;
        UnifyPayPlugin.getInstance(this).sendPayRequest(msg);
    }

    /**
     * 云闪付
     *
     * @param appPayRequest
     */
    private void payCloudQuickPay(String appPayRequest) {
        String tn = "空";
        try {
            JSONObject e = new JSONObject(appPayRequest);
            tn = e.getString("tn");
        } catch (JSONException e1) {
            e1.printStackTrace();
        }
        UPPayAssistEx.startPay(this, null, null, tn, "00");
        Log.d("test", "云闪付支付 tn = " + tn);
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
                    return null;
                } else {
                    return EntityUtils.toByteArray(response.getEntity());
                }
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
