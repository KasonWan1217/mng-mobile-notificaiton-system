package object;

import com.google.gson.Gson;
import object.ResponseMessage.Message;
import java.util.HashMap;

public class FunctionStatus {
    private boolean status;
    private HashMap<String, Object> response;
    private Integer status_code;
    private String error_msg;
    private String error_msg_detail;

    public FunctionStatus(boolean status, HashMap<String, Object> response) {
        this.status = status;
        this.response = response;
    }

    public FunctionStatus(boolean status, Integer status_code, String error_msg) {
        this.status = status;
        this.status_code = status_code;
        this.error_msg = error_msg;
        this.error_msg_detail = error_msg;
    }

    public FunctionStatus(boolean status, Integer status_code, String error_msg, String error_msg_detail) {
        this.status = status;
        this.status_code = status_code;
        this.error_msg = error_msg;
        this.error_msg_detail = error_msg_detail;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public HashMap<String, Object> getResponse() {
        return response;
    }

    public void setResponse(HashMap<String, Object> response) {
        this.response = response;
    }

    public Integer getStatus_code() {
        return status_code;
    }

    public void setStatus_code(Integer status_code) {
        this.status_code = status_code;
    }

    public String getError_msg() {
        return error_msg;
    }

    public void setError_msg(String error_msg) {
        this.error_msg = error_msg;
    }

    public String getError_msg_detail() {
        return error_msg_detail;
    }

    public void setError_msg_detail(String error_msg_detail) {
        this.error_msg_detail = error_msg_detail;
    }

    public String convertToJsonString() {
        return new Gson().toJson(this);
    }

//    public ResponseMessage convertToResponseMessage(){
//        if (this.isStatus()) {
//            Gson gson = new Gson();
//            Message message = gson.fromJson(gson.toJson(this.getResponse()), Message.class);
//            return new ResponseMessage(200, message);
//        } else {
//            Message message = new Message(this.getStatus_code(), this.getError_msg(), this.getError_msg_detail());
//            return new ResponseMessage(this.getStatus_code(), message);
//        }
//    }

    public ResponseMessage.Message convertToMessage(){
        if (this.isStatus()) {
            Gson gson = new Gson();
            Message message = gson.fromJson(gson.toJson(this.getResponse()), Message.class);
            return message;
        } else {
            Message message = new Message(this.getStatus_code(), this.getError_msg(), this.getError_msg_detail());
            return message;
        }
    }
}
