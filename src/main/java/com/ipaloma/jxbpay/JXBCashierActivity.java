package com.ipaloma.jxbpay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Debug;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.json.JSONException;
import org.json.JSONObject;

public class JXBCashierActivity extends AppCompatActivity
        implements GetPayRequestTask.GetPayRequestResponse,
                            PaymentStatusTask.PaymentStatusResponse {

    private static final String TAG = "JXBCashierActivity";
    private Intent mStartIntent;
    private JSONObject parameter;
    private String mResultUrl;
    private Activity mActivity;
    String qrCode = "";
    private TextView loadQr;
    private ImageView qrPay;
    private TextView cashTotal;
    private TextView customer;
    private TextView tickText;
    private AsyncTask<Void, Void, String> mPayRequestTask;
    private AsyncTask<Void, Void, String> mPaymentStatusTask;

    private int mQrDuration = 60*1000;
    private long qrTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = this;
        mStartIntent = getIntent();
        if(mStartIntent.getExtras() != null)
            Log.d(TAG, "start parameter" + mStartIntent.getExtras().toString());

        try {
            String para_str = mStartIntent.getStringExtra("parameter");

            parameter = new JSONObject(para_str);
            parameter.putOpt("amount", parameter.optDouble("count"));
            parameter.remove("count");
            parameter.putOpt("billnumber", parameter.optString("billid", ""));
            parameter.putOpt("method", "qrpay");
            parameter.putOpt("pay_type", "c2b聚合支付");
            parameter.putOpt("no_redirect", true);
        } catch (JSONException e) {
            Log.e(TAG, "error parameters", e);
            e.printStackTrace();
            parameter = new JSONObject();
        }

        setContentView(R.layout.activity_jxbcashier);

        //显示收款场景
        String category = parameter.optString("scene", "");
        TextView title = (TextView)findViewById(R.id.toolbar_title);
        title.setText("经销宝收银台" + (category.equals("") ? "" : "-") + category);

        customer = (TextView)findViewById(R.id.customer_name);
        if(!parameter.optString("customer_name", "").equals(""))
            customer.setText(parameter.optString("customer_name"));

        cashTotal = (TextView)findViewById(R.id.cash_total);
        double amount = parameter.optDouble("amount", 0);
        String amount_text = parameter.optString("amount", "");

        if(amount > 0)
            cashTotal.setText(String.format("￥%,.2f元", amount));
        else if (!amount_text.equals(""))
            cashTotal.setText(String.format("￥%s元", amount_text));

        loadQr = (TextView)findViewById(R.id.load_qr_hint);
        qrPay = (ImageView)findViewById(R.id.qr);

        loadQr.setVisibility(View.VISIBLE);
        qrPay.setVisibility(View.INVISIBLE);

        loadQr.setClickable(false);

        tickText = (TextView)findViewById(R.id.tick_text);

    }

    @Override
    protected void onResume(){
        super.onResume();

        // 支付成功
        if(qrTime < 0) {
            Log.d(TAG, "resumed, nothing to do");
            return;
        }
        // 当前没有展示二维码，或者二维码10秒以内到期
        if(qrPay.getVisibility() == View.INVISIBLE
                || (System.currentTimeMillis() - qrTime) > mQrDuration - 10*1000 ) {
            Log.d(TAG, "resumed, to start new pay request");
            if (mPayRequestTask != null && mPayRequestTask.getStatus() != AsyncTask.Status.FINISHED)
                mPayRequestTask.cancel(true);
            mPayRequestTask = new GetPayRequestTask(parameter, mActivity, JXBCashierActivity.this)
                    .execute();
            return;
        }

        // 如果二维码可见，可用，启动状态查询，限定时间为剩余时间
        Log.d(TAG, "resumed, to start new payment status check " + (mQrDuration - System.currentTimeMillis() + qrTime) + " millis");
        if(mPaymentStatusTask != null && mPaymentStatusTask.getStatus() != AsyncTask.Status.FINISHED)
            mPaymentStatusTask.cancel(true);
        mPaymentStatusTask = new PaymentStatusTask(mResultUrl, mQrDuration - System.currentTimeMillis() + qrTime, this, this)
                .execute();

    }
    @Override
    protected void onStop(){
        super.onStop();

        CancelTasks();
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        CancelTasks();
    }
    private void CancelTasks(){
        if(mPaymentStatusTask != null && mPaymentStatusTask.getStatus() != AsyncTask.Status.FINISHED)
            mPaymentStatusTask.cancel(true);
        if(mPaymentStatusTask != null && mPaymentStatusTask.getStatus() != AsyncTask.Status.FINISHED)
            mPaymentStatusTask.cancel(true);
    }
    @Override
    public synchronized void  onPayRequest(String result) {

        // Stop the previous payment status
        if(mPaymentStatusTask!=null && mPaymentStatusTask.getStatus() != AsyncTask.Status.FINISHED)
            mPaymentStatusTask.cancel(true);
        tickText.setText("");
        tickText.setVisibility(View.INVISIBLE);

        Log.i(TAG, "onPostExecute-->" + result);
        int retCode = RESULT_CANCELED;
        String retMessage="";
        JSONObject json=null;

        try {

            json = new JSONObject(result == null ? "{\"error\":\"网络开小差了\"}" : result);
            String error = json.optString("error", "").trim();
            mResultUrl = json.optString("result_url", "");  // 从服务器返回的状态查询 url

            qrCode = json.optString("qrcode", "");

            retCode = error.equals("") && !mResultUrl.equals("") && !qrCode.equals("") ? RESULT_OK : RESULT_CANCELED;
            retMessage = error;

            mStartIntent.putExtra("result_url", mResultUrl);

        }catch (JSONException e){
            Log.e(TAG, "exception:", e);
            retCode = RESULT_CANCELED;
            retMessage = "网络开小差了";
            try {
                json = new JSONObject("{\"error\":\"网络开小差了\"}");
            } catch (JSONException e1) {
                e1.printStackTrace();
            }
        }
        // 已经支付成功
        if(json != null && json.optString("sessionstatus", "").equals("成功")){
            onPaymentSuccess(json);
            return;
        }

        if(retCode == RESULT_CANCELED) {
            loadQr.setVisibility(View.VISIBLE);
            qrPay.setVisibility(View.INVISIBLE);
            loadQr.setText(String.format(getString(R.string.get_qr_fail),retMessage));
            loadQr.setClickable(true);

            loadQr.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new GetPayRequestTask(parameter, mActivity, JXBCashierActivity.this).execute();
                    loadQr.setClickable(false);
                }
            });
            return;
        }
        loadQr.setVisibility(View.INVISIBLE);
        qrPay.setVisibility(View.VISIBLE);
        Bitmap bitmap = QRCodeHelper
                .newInstance(mActivity)
                .setContent(qrCode)
                .setErrorCorrectionLevel(ErrorCorrectionLevel.Q)
                .setMargin(2)
                .getQRCOde();
        qrPay.setImageBitmap(bitmap);

        qrTime = System.currentTimeMillis();

        if(mPaymentStatusTask != null && mPaymentStatusTask.getStatus() != AsyncTask.Status.FINISHED)
            mPaymentStatusTask.cancel(true);
        mPaymentStatusTask = new PaymentStatusTask(mResultUrl, mQrDuration, this, this).execute();

        Log.d(TAG, retMessage);
    }

    @Override
    public void beforePayRequest(JSONObject param){
        qrTime = 0;
        if(mPaymentStatusTask!=null && mPaymentStatusTask.getStatus() != AsyncTask.Status.FINISHED)
            mPaymentStatusTask.cancel(true);
        loadQr.setText("正在加载收款码......");
        tickText.setText("");
        qrPay.setVisibility(View.INVISIBLE);
        loadQr.setVisibility(View.VISIBLE);
        tickText.setVisibility(View.VISIBLE);

    }

    @Override
    public void onPayRequestTick(long millisUntilFinished) {
        tickText.setText(String.format("%d", millisUntilFinished/1000));
    }

    @Override
    public synchronized void onPaymentStatus(String output) {

        tickText.setText("");
        tickText.setVisibility(View.INVISIBLE);

        JSONObject result = null;
        try {
            Log.d(TAG, output==null ? "null" : output);
            result = new JSONObject(output);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 没有正确结果
        if(result == null || !result.optString("error","").equals("")
            || !result.optString("sessionstatus", "").equals("成功")){
            onPaymentFailed(result);
            return;
        }

        onPaymentSuccess(result);

    }

    @Override
    public void beforePaymentStatus(String url) {
        tickText.setText("");
        tickText.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPayementStatustTick(long millisUntilFinished) {
        tickText.setText(String.format("%d", millisUntilFinished/1000));
    }

    private void onPaymentFailed(final JSONObject result) {

        loadQr.setVisibility(View.VISIBLE);
        qrPay.setVisibility(View.INVISIBLE);
        String message =
                // 网络问题
                result == null ? getString(R.string.payment_status_network_error)
                        // 查询返回错误信息
                        : !result.optString("error", "").equals("")
                            ? String.format(getString(R.string.payment_status_error), result.optString("error", ""))
                        // 单据关闭/失败
                        : getString(R.string.payment_status_pending);

        loadQr.setText(message);

        loadQr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 重新获取二维码，继续扫码
                // 网络问题/继续等待，重新发起支付，避免状态不一致
                new GetPayRequestTask(parameter, mActivity, JXBCashierActivity.this)
                .execute();
            }
        });
        loadQr.setClickable(true);
        return;
    }
    private void onPaymentSuccess(JSONObject result){
        qrTime = -1;
        loadQr.setVisibility(View.VISIBLE);
        qrPay.setVisibility(View.INVISIBLE);
        loadQr.setText(String.format(getString(R.string.pay_done), result.optDouble("order_amount")));

        loadQr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActivity.finish();
                // TODO notify the caller with result + input parameters
            }
        });
        loadQr.setClickable(true);
    }
}
