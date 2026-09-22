package org.hvtra.commonservice.logging;
//Mapped Diagnostic Context: gắn thêm context vào log của một request
public class MdcKeys {

       private MdcKeys() {
           }

   public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String SERVICE = "service";
}
