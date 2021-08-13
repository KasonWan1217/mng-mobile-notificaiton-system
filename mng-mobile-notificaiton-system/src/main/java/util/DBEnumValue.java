package util;

public class DBEnumValue {
    public enum AppName {
        BEA_APP_Group
    }
    public enum MobileType {
        iOS,
        Android
    }
    public enum ArnType {
        Topic,
        Platform
    }
    public enum TargetType {
        Group,
        Personal
    }
    public enum Status {
        Success,
        Fail,
        Reset
    }

    public boolean equalValue(String passedValue){
        return this.toString().equals(passedValue);
    }
}
