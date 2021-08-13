package util;

import object.FunctionStatus;
import object.ResponseMessage;

public class ErrorMessageUtil {
    public enum ErrorMessage {
        AmazonClient_Error(700, "AmazonClient_Error"),                                  //700
        Json_Request_Error(704, "Json_Request_Error"),                                  //704
        Parameter_Missing_Error(705, "Parameter_Missing_Error"),                        //705
        Invalid_Length_Parameter(706, "Invalid_Length_Parameter"),                      //706
        Invalid_Value_Parameter(707, "Invalid_Value_Parameter"),                        //707
        PlatformName_Null_Error(710, "PlatformName_Null_Error"),                        //710
        AppRegId_Invalid_Error(711, "AppRegId_Invalid_Error"),                          //711
        AppRegId_Null_Error(712,"AppRegId_Null_Error"),                                 //712
        AppRegId_Inactive_Error(713,"AppRegId_Inactive_Error"),                         //713
        DynamoDB_Insert_Error(730,"DynamoDB_Insert_Error"),                             //730
        DynamoDB_Update_Error(731,"DynamoDB_Update_Error"),                             //731
        DynamoDB_Delete_Error(732,"DynamoDB_Delete_Error"),                             //732
        DynamoDB_Query_Error(733,"DynamoDB_Query_Error"),                               //733
        Sns_Registration_Error(770,"Sns_Registration_Error"),                           //770
        Sns_Unregister_Error(771,"Sns_Unregister_Error"),                               //771
        Sns_Subscription_Error(772,"Sns_Subscription_Error"),                           //772
        Sns_Unsubscribe_Error(773,"Sns_Unsubscribe_Error"),                             //773
        Sns_Publish_Notification_Error(774,"Sns_Publish_Notification_Error"),           //774
        Topic_subscription_Already_Error(780,"Topic_subscription_Already_Error"),   //780
        Topic_subscription_List_Null_Error(781,"Topic_subscription_List_Null_Error"),   //781
        Request_Format_Error(500,"Request_Format_Error");                               //500

        private final Integer code;
        private final String error_msg;

        private ErrorMessage(int code, String error_msg) {
            this.code = code;
            this.error_msg = error_msg;
        }

        public Integer getCode() {
            return code;
        }

        public String getError_msg() {
            return error_msg;
        }
    }
    public static FunctionStatus getFunctionStatus(ErrorMessage fail_message, String fieldName) {
        return new FunctionStatus(false ,fail_message.getCode(), fail_message.getError_msg(), fieldName + ": " + fail_message.getError_msg());
    }
    public static FunctionStatus getFunctionStatus(ErrorMessage fail_message) {
        return new FunctionStatus(false ,fail_message.getCode(), fail_message.getError_msg());
    }

    public static ResponseMessage getErrorResponseMessage(ErrorMessage fail_message) {
        return new ResponseMessage(fail_message.getCode(), fail_message.getError_msg());
    }

}
