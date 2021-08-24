package util;

import object.FunctionStatus;

import java.util.Arrays;

public class CommonValidationUtil {

    public static FunctionStatus ckLength_Mandatory(String input, int minLength, int maxLength, String fieldName) {
        if (isEmpty(input))
            return ErrorMessageUtil.getFunctionStatus(ErrorMessageUtil.ErrorMessage.Parameter_Missing_Error, fieldName);
        else if(! isValidLength(input, minLength, maxLength))
            return ErrorMessageUtil.getFunctionStatus(ErrorMessageUtil.ErrorMessage.Invalid_Length_Parameter, fieldName);
        else
            return new FunctionStatus(true, null);
    }

    public static FunctionStatus ckLength_nonMandatory(String input, int minLength, int maxLength, String fieldName) {
        if (! isValidLength_nonMandatory(input, minLength, maxLength))
            return ErrorMessageUtil.getFunctionStatus(ErrorMessageUtil.ErrorMessage.Invalid_Length_Parameter, fieldName);
        else
            return new FunctionStatus(true, null);
    }

    public static FunctionStatus ckValue_Mandatory(String input, Enum[] objects, String fieldName) {
        if (!stringContainsItemFromList(input, objects))
            return ErrorMessageUtil.getFunctionStatus(ErrorMessageUtil.ErrorMessage.Invalid_Value_Parameter, fieldName);
        else
            return new FunctionStatus(true, null);
    }

    public static FunctionStatus ckValue_Mandatory(int input, int[] items, String fieldName) {
        if (!intContainsItemFromList(input, items))
            return ErrorMessageUtil.getFunctionStatus(ErrorMessageUtil.ErrorMessage.Invalid_Value_Parameter, fieldName);
        else
            return new FunctionStatus(true, null);
    }

    public static boolean isNull(Object input) {
        if (input == null) {
            return true;
        }
        return false;
    }
    public static boolean isEmpty(String input) {
        if (input == null || "".equals(input)) {
            return true;
        }
        return false;
    }

    public static boolean isValidLength_nonMandatory(String input, int length) {
        return isValidLength_nonMandatory(input, length, length);
    }

    public static boolean isValidLength_nonMandatory(String input, int minLength, int maxLength) {
        if (isEmpty(input))
            return true;
        return isValidLength(input, minLength, maxLength);
    }

    public static boolean isValidLength(String input, int length) {
        return isValidLength(input, length, length);
    }

    public static boolean isValidLength(String input, int minLength, int maxLength) {
        if (input.length() < minLength || input.length() > maxLength) {
            return false;
        }
        return true;
    }
    public static boolean intContainsItemFromList(int input, int[] items) {
        return Arrays.stream(items).anyMatch(item -> item == input);
    }
    public static boolean stringContainsItemFromList(String input, Enum[] objects) {
        String[] strArr = Arrays.stream(objects).map(Enum::name).toArray(String[]::new);
        return Arrays.stream(strArr).anyMatch(input::contains);
    }

    public static boolean isValidValue(String input, DBEnumValue enumValue) {
        if (enumValue.equalValue(input)) {
            return true;
        }
        return false;
    }

}
