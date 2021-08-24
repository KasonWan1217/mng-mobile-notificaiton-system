package object;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import object.db.InboxRecord;

public class PushMessage {
    @SerializedName("default")
    private String default_value;

    @SerializedName("APNS")
    private String apns;

    @SerializedName("APNS_SANDBOX")
    private String apns_sandbox;

    @SerializedName("GCM")
    private String gcm;

    public String getDefault_value() {
        return default_value;
    }

    public void setDefault_value(String default_value) {
        this.default_value = default_value;
    }

    public String getApns() {
        return apns;
    }

    public String getApns_sandbox() {
        return apns_sandbox;
    }

    public String getGcm() {
        return gcm;
    }

    public void setApns(String apns) {
        this.apns = apns;
    }

    public void setApns_sandbox(String apns_sandbox) {
        this.apns_sandbox = apns_sandbox;
    }

    public void setGcm(String gcm) {
        this.gcm = gcm;
    }

    public class Alert {
        private NotificationDetails alert;

        public Alert(NotificationDetails alert) {
            this.alert = alert;
        }

        public NotificationDetails getAlert() {
            return alert;
        }

        public void setAlert(NotificationDetails alert) {
            this.alert = alert;
        }
    }

    public class APNS {
        private Alert aps;

        public APNS(Alert aps) {
            this.aps = aps;
        }

        public Alert getAps() {
            return aps;
        }

        public void setAps(Alert aps) {
            this.aps = aps;
        }
    }

    public class GCM {
        private NotificationDetails data;

        public NotificationDetails getData() {
            return data;
        }

        public void setData(NotificationDetails data) {
            this.data = data;
        }

        public GCM(NotificationDetails data) {
            this.data = data;
        }
    }

    public PushMessage(InboxRecord obj){
        NotificationDetails details = new NotificationDetails(obj);
        Gson gson = new GsonBuilder().serializeNulls().create();
        String txt_APNS = gson.toJson(new APNS(new Alert(details)));
        String txt_GCM = gson.toJson(new GCM(details));

        this.default_value = "BEA Notification Message";
        this.apns = txt_APNS;
        this.apns_sandbox = txt_APNS;
        this.gcm = txt_GCM;
    }

    public class NotificationDetails {
        private String msg_id;
        private int notification_id;
        private String action_category;
        private String title;
        private String sub_title;
        private String body;
        private String sound;
        private int badge;
        private String pic_url;

        public NotificationDetails(InboxRecord obj) {
            this.msg_id = obj.getMsg_id();
            this.notification_id = obj.getNotification_id();
            this.action_category = obj.getAction_category();
            this.title = obj.getMessage().getTitle();
            this.sub_title = obj.getMessage().getSub_title();
            this.body = obj.getMessage().getBody();
            this.sound = obj.getSound();
            this.badge = obj.getBadge();
            this.pic_url = obj.getPic_url();
        }

        public String getMsg_id() {
            return msg_id;
        }

        public void setMsg_id(String msg_id) {
            this.msg_id = msg_id;
        }

        public int getNotification_id() {
            return notification_id;
        }

        public void setNotification_id(int notification_id) {
            this.notification_id = notification_id;
        }

        public String getAction_category() {
            return action_category;
        }

        public void setAction_category(String action_category) {
            this.action_category = action_category;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSub_title() {
            return sub_title;
        }

        public void setSub_title(String sub_title) {
            this.sub_title = sub_title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public String getSound() {
            return sound;
        }

        public void setSound(String sound) {
            this.sound = sound;
        }

        public int getBadge() {
            return badge;
        }

        public void setBadge(int badge) {
            this.badge = badge;
        }

        public String getPic_url() {
            return pic_url;
        }

        public void setPic_url(String pic_url) {
            this.pic_url = pic_url;
        }
    }
}
