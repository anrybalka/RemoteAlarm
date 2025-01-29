package ru.devmentalhydra.remotealarm;

import java.util.Calendar;
import java.util.Date;

public class ContainerResp {
    boolean authStatus;
    Date date;
    String exeComand;
    String exeComandArg1;
    String exeComandResult;


    public ContainerResp(Calendar cal) {
        authStatus = false;
        date = cal.getTime();
        exeComand = "";
        exeComandArg1 = "";
        exeComandResult = "";
    }
}
