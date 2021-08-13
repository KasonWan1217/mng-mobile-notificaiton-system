package util;

import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class CommonUtil {

    public static String getSnsTopicArn(String topic) {
        String prefix = System.getenv("SnsTopicDomain");
        return prefix + topic;
    }

    public static String getSnsPlatformArn(String topic) {
        String prefix = System.getenv("SnsPlatformDomain");
        return prefix + topic;
    }

    public static boolean validate_AppRegId(String app_reg_id) {
        return genIdentifierDigit(app_reg_id.substring(0, 19)).equals(app_reg_id.substring(19, 20));
    }

    public static String getNewAppRegID(String app_id, String strDate) {
        try {
            String newAppRegID = genNewAppRegID(app_id, new SimpleDateFormat("yyyyMMddHHmmss").parse(strDate));
            return newAppRegID + genIdentifierDigit(newAppRegID);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String genNewAppRegID(String app_id, Date datetime) {
        String strDate = new SimpleDateFormat("yyDDDHHmmss").format(datetime);
        int randNum = new Random().nextInt(99999);
        return app_id + strDate + String.format("%06d", randNum);
    }

    private static String genIdentifierDigit(String str) {
        int initNum = Integer.parseInt("BEA", 16)*12;
        int sum = 0;
        for (int i = 0; i < str.length(); i++) {
            sum += Integer.parseInt(str.substring(i, i+1), 16) * (str.length()+1 - i);
        }
        sum += initNum;
        String checkDigit = Integer.toHexString(sum % 16);
        return checkDigit.toUpperCase();
    }

    public static int genNotificationID(String msg_id, String cTime) {
        try {
            Date datetime = new SimpleDateFormat("yyyyMMddHHmmss").parse(cTime);
            String rs = new SimpleDateFormat("DDD").format(datetime) + getLastWord(msg_id, 13);
            return Integer.parseInt(rs);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static String getLastWord(String str, int length){
        return str.substring(length, str.length());
    }

    public static String getCurrentTime() {
        DateFormat datetime = new SimpleDateFormat("yyyyMMddHHmmss");
        datetime.setTimeZone(TimeZone.getTimeZone("Asia/Hong_Kong"));
        return datetime.format(new Date());
    }
}
