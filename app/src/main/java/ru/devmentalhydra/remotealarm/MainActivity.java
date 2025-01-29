package ru.devmentalhydra.remotealarm;


import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.TextView;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    TextView tvCountStartThread1;
    TextView tvIP; // для отображения IP-адреса
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvCountStartThread1 = findViewById(R.id.tvCountStartThread1);
        tvIP = findViewById(R.id.tvIP);
        startService(new Intent(getApplication(),AlarmServiceServer.class));
        // Регистрируем BroadcastReceiver для приёма сообщений из сети
        IntentFilter filter = new IntentFilter("custom.action.STRING_RECEIVED");
        registerReceiver(messageReceiver, filter);

        // Получаем и отображаем IP-адрес
        String ipAddress = getDeviceIpAddress();
        if (ipAddress != null) {
            tvIP.setText("IP Address: " + ipAddress);
        } else {
            tvIP.setText("IP Address: Unknown");
        }
    }

    // Метод для получения IP-адреса устройства
    private String getDeviceIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (isIPv4) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onStart(){
        super.onStart();
        //отображает на экране кол-во запусков/перезапусков серверного потока
        tvCountStartThread1.setText(String.valueOf(AlarmServiceServer.countStartMainThreadServer));
        //проверка разрешений и запрос методов
        if(CheckPermissionsAndRequest()){
            hideIfFirstRunAfterStartUp();
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // отменяет регистрацию BroadcastReceiver
        unregisterReceiver(messageReceiver);
    }

    //проверяет разрешения если всё ок возвращает true, если не ок запрашивает разрешения и возвращает false
    private boolean CheckPermissionsAndRequest(){
        //проверяет разрешения если всё ок возвращает true, если не ок запрашивает разрешения и возвращает false
        Boolean result = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean isPermissionDrawOverlaysOk = Settings.canDrawOverlays(getApplicationContext());
            boolean isPermissionBatteryOk = isBatteryOptimizationDisabled(getApplicationContext());
            //ПРОВЕРКА РАЗРЕШЕНИЙ если нет разрешения к батареи, (нет разрешения к отображению поверх экрана и при этом >= 10 андроиду)
            if (!isPermissionBatteryOk || (!isPermissionDrawOverlaysOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) {
                //разрешение не экономить заряд батаре
                if (!isPermissionBatteryOk){
                    AlertDialog.Builder builder1 = new AlertDialog.Builder(MainActivity.this);
                    builder1.setTitle("Запрос разрешения");
                    builder1.setMessage("Для постоянной фоновой работы приложению " + getResources().getString(R.string.app_name) + " требуется разрешение на \"не эконмить заряд батареи\"");
                    builder1.setCancelable(false);
                    builder1.setPositiveButton(
                            "Не экономить",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    try {
                                        if(!isBatteryOptimizationDisabled(getApplicationContext())) {
                                            requestBatteryOptimizationPermission(getApplicationContext());
                                        }
                                    }
                                    catch (Exception e){
                                        showUserAlarmMessageBox(e);
                                    }
                                    dialog.cancel();
                                }
                            });
                    AlertDialog alert1 = builder1.create();
                    alert1.show();
                }
                if (!isPermissionDrawOverlaysOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                    AlertDialog.Builder builder2 = new AlertDialog.Builder(MainActivity.this);
                    builder2.setTitle("Запрос разрешения");
                    builder2.setMessage("Для автозапуска приложению " + getResources().getString(R.string.app_name) + " требуется разрешение на отображение поверх других приложений");
                    builder2.setCancelable(false);
                    builder2.setPositiveButton(
                            "Отображать поверх",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    try {
                                        requestOverlayPermission(getApplicationContext());
                                    }
                                    catch (Exception e){
                                        showUserAlarmMessageBox(e);
                                    }
                                    dialog.cancel();
                                }
                            });
                    AlertDialog alert2 = builder2.create();
                    alert2.show();
                }
            }
            else {result = true;}
        }
        else {result = true;}
        return result;
    }


    // Логика Обработчика приёмника, для отображения сообщений из сервиса из сети
    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("custom.action.STRING_RECEIVED".equals(intent.getAction())) {
                Intent i = new Intent(context, MainActivity.class);
                //i.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
                i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(i);
                String message = intent.getStringExtra("message");
                showMessageFromAdmin("Сообщение от администратора", message);
            }
        }
    };

    // Отображение ошибки запроса разрешения
    public void showUserAlarmMessageBox(Exception e){
        AlertDialog.Builder builder3 = new AlertDialog.Builder(MainActivity.this);
        builder3.setTitle("Ошибка запроса разрешения");
        builder3.setMessage("Обратитесь в тех. поддержку, чтобы они запустили приложение в admin моде с активированными настройками. " + e.toString());
        builder3.setCancelable(true);
        builder3.setPositiveButton(
                "Скрыть",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        hideIfFirstRunAfterStartUp();
                    }
                });
        AlertDialog alert3 = builder3.create();
        alert3.show();
    }


    //прячет окно на задний фон после автозапуска
    public void hideIfFirstRunAfterStartUp(){
        Boolean isAutorun = getIntent().getBooleanExtra("autorun", false);
        if (isAutorun) {
            moveTaskToBack(true);
            getIntent().removeExtra("autorun");
        }
    }

    //показывает сообщение от администратора из сети
    public void showMessageFromAdmin(String tittle, String message){
        AlertDialog.Builder builder3 = new AlertDialog.Builder(MainActivity.this);
        builder3.setTitle(tittle);
        builder3.setMessage(message);
        builder3.setCancelable(false);
        builder3.setPositiveButton(
                "Ок",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        moveTaskToBack(true);
                    }
                });
        AlertDialog alert3 = builder3.create();
        alert3.show();
    }

    // Метод для проверки разрешения на режим "не экономить заряд батареи"
    public static boolean isBatteryOptimizationDisabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = context.getPackageName();
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return powerManager != null && powerManager.isIgnoringBatteryOptimizations(packageName);
        }
        return true; // Для более старых версий Android предполагаем, что разрешение уже есть
    }

    // Метод для запроса разрешения отображать поверх приложенией
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static void requestOverlayPermission(Context context) {
        Intent myIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
        myIntent.setData(uri);
        myIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(myIntent);
    }

    // Метод для запроса разрешения на режим "не экономить заряд батареи"
    public static void requestBatteryOptimizationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }


}