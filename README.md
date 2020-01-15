# ipa-cashier
##功能
提供收银台核心功能
  支付方式：APP/QR/H5/公众号支付
  支付渠道：微信/支付宝/云闪付
使用环境
  提供三个环境
  dev/demo/product  分别对应开发，演示，生产环境
调用参数
  pay_type: 微信/支付宝/云闪付
  method:   h5pay/qrpay/apppay  
  amount:   收款金额，以元为单位，两位小数
  sandbox:  子商户二级域名，一般为注册手机号
  billnumber: 订单号，要求在一个子商户中唯一
  notifyurl:  调用方提供的回调地址用于接收支付状态通知，POST方式，json参数 application/json; charset=utf-8
  returnurl:  支付后跳转页面，支付方式为h5pay时使用
返回结果
  公众号支付
    {
      "url": "https://xxx?msgId=62b2d91937b047eab217ad5f21272a13&requestTimestamp=2020-01-15+10%3a58%3a22&merOrderId=3194202001151058215850000001&mid=898340149000005&tid=001&attachedData=%7b%22flowrootid%22%3anull%2c%22sandbox%22%3a%2218531828656%22%2c%22guid%22%3a%2226da6c892df548dba1ecc40207267d8a%22%2c%22notify_url%22%3anull%7d&notifyUrl=http%3a%2f%2fliubin-2018.imdo.co%2fwebapi%2fasset%2foutertransfer%2fpay%2fnotify%2fchinaums%2f3194202001151058215850000001&instMid=YUEDANDEFAULT&originalAmount=1&totalAmount=1&subAppId=12345&msgType=WXPay.jsPay&msgSrc=WWW.TEST.COM&sign=CCDE393612DCBCE358849C26CB1A648C",
      "method": "get",
      "result_url": "http://xxx/webapi/asset/outertransfer/status?billnumber=202001070058150000006"
    }
    调用者在微信中跳转到url指定页面，可通过result_url自主查询支付结果，或者通过传递的notifyurl接收通知消息
  h5支付  
    {
      "url": "https://xxx?msgId=62b2d91937b047eab217ad5f21272a13&requestTimestamp=2020-01-15+10%3a58%3a22&merOrderId=3194202001151058215850000001&mid=898340149000005&tid=001&attachedData=%7b%22flowrootid%22%3anull%2c%22sandbox%22%3a%2218531828656%22%2c%22guid%22%3a%2226da6c892df548dba1ecc40207267d8a%22%2c%22notify_url%22%3anull%7d&notifyUrl=http%3a%2f%2fliubin-2018.imdo.co%2fwebapi%2fasset%2foutertransfer%2fpay%2fnotify%2fchinaums%2f3194202001151058215850000001&instMid=YUEDANDEFAULT&originalAmount=1&totalAmount=1&subAppId=12345&msgType=WXPay.jsPay&msgSrc=WWW.TEST.COM&sign=CCDE393612DCBCE358849C26CB1A648C",
      "method": "get",
      "result_url": "http://xxx/webapi/asset/outertransfer/status?billnumber=202001070058150000006"
    }
    调用者在浏览器中跳转到url指定页面，可通过result_url自主查询支付结果，或者通过传递的notifyurl接收通知消息
  扫码支付
    {
      "qrcode": "https://xxx?id=31942001154593417000039638",
      "result_url": "http://xxx/webapi/asset/outertransfer/status?billnumber=202001070058150000006"
    }
    调用者在将qrcode内容编码，并显示在界面中，可通过result_url自主查询支付结果，或者通过传递的notifyurl接收通知消息
  APP支付
    {
    "app_pay_request": {"xxx":"xxxx"},
    "result_url": "http://xxx/webapi/asset/outertransfer/status?billnumber=202001070058150000006"
    }
    调用者在将app_pay_reqeust内容作为参数，调用相应的APP支付sdk，可通过result_url自主查询支付结果，或者通过传递的notifyurl接收通知消息
  错误信息
    如果调用发生错误，同样用json返回结果：
    {
      "error":"没有注册的用户"
    }
    调用者检查error内容是否为空字符串/null，否则就表示获得了正确的结果
  支付通知
    post，body：json
    {
      "sessionstatus":"成功|失败|关闭|等待|退款", // 交易状态
      "sessionid":"1234xxxxx",                  // 支付订单号
      "sessionendtime":"2020-1-20 09:01:29",    // 支付时间
      "transaction_no":"1234xxx",               // 支付机构流水号
      "receipt_amount":1000.00,                 // 商户实收金额
      "order_amount":1000.00,                   // 商户订单金额
      "settle_date":"2020-1-20"                 // 结算日期
    }
   
  
