package ru.devmentalhydra.remotealarm;


import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlarmServiceServer extends Service {
    ServerSocket serverSocket;
    public static final int SERVER_PORT = 4321;
    public static int countStartMainThreadServer = 0;
    public static boolean isAlarmOn = false;
    public NotificationManager notificationManager;
    public static final int NOTIFY_ID = 1;
    public static final String CHANNEL_ID = "Статус службы";
    private MainThreadServer mainThreadServer;
    private ExecutorService executorService;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
        startMainThreadServer();
    }

    @Override
    public void onDestroy() {
        if (mainThreadServer != null) {
            mainThreadServer.stopRunning();
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
        super.onDestroy();
    }

    class MainThreadServer implements Runnable {
        private volatile boolean running = true;
        @Override
        public void run() {
            Socket socket;
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                startNotify();
                try {
                    while (running){
                        socket = serverSocket.accept();
                        handleConnection(socket);
                    }
                    if (!running){
                        serverSocket.close();
                    }
                } catch (Exception e) {
                    Log.e("accept", "Ошибка в блоке accept", e);
                    serverSocket.close();
                    startMainThreadServer();
                }
            } catch (Exception e) {
                Log.e("new ServerSocket", "Ошибка в new ServerSocket", e);
                //e.printStackTrace();
                //startThread1();
            }
        }
        public void stopRunning() {
            running = false;
        }
        private void handleConnection(Socket socket) {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                OutputStream out = socket.getOutputStream(); // Add this line
                String inputLine;
                List<String> requestList = new ArrayList<>();
                while ((inputLine = in.readLine()) != null && !inputLine.isEmpty()) {
                    requestList.add(inputLine);
                }
                String getReq = requestList.get(0);

                Calendar cal = Calendar.getInstance();
                ContainerResp containerResp = new ContainerResp(cal);

                String responseCode = "HTTP/1.1 401 Unauthorized\r\n";
                int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
                if (getReq.contains("GET /")) {
                    if (getReq.contains("pass="+dayOfMonth+"ozonadmin")) {
                        containerResp.authStatus = true;
                        responseCode = "HTTP/1.1 200 OK\r\n";
                        if (getReq.contains("message=")) {
                            containerResp.exeComand = "message";
                            String urlMessage = getReq.substring(getReq.indexOf("message=") + 8, getReq.indexOf(" HTTP"));
                            String message = URLDecoder.decode(urlMessage);
                            Intent intent = new Intent("custom.action.STRING_RECEIVED");
                            intent.putExtra("message", message);
                            sendBroadcast(intent);
                            containerResp.exeComandArg1 = message;
                            containerResp.exeComandResult = "Message sent successfully.";
                        }
                        else if (getReq.contains("alarm=")) {
                            containerResp.exeComand = "alarm";
                            String strVolume = getReq.substring(getReq.indexOf("alarm=") + 6, getReq.indexOf(" HTTP"));
                            Integer numVolume = tryParseInt(strVolume, 100);
                            containerResp.exeComandArg1 = strVolume;
                            if (!AlarmServiceServer.isAlarmOn){
                                containerResp.exeComandResult = "Alarm started on device.";
                            }
                            else {
                                containerResp.exeComandResult = "The device did NOT trigger an alarm because it has already been launched and has not ended.";
                            }
                            doAlarm(numVolume);
                        }
                        else {
                            containerResp.exeComandResult = "This command does not exist. Nothing happened. Commands list: 1) message={text}(message=hello); 2) alarm={volume}(alarm=5)";
                        }
//                        else if (getReq.contains("newAuth")) {
//                            // Handle password update
//                        }
                    }
                }
                Gson gson = new Gson();
                String jResp = gson.toJson(containerResp);
                String response = responseCode +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        "Content-Length: " + jResp.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                        "\r\n" +
                        jResp;
                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.flush();

                in.close();
                out.close(); // Close the output stream
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startNotify(){
        notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        Intent notificationIntent = new Intent(AlarmServiceServer.this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(AlarmServiceServer.this,
                0, notificationIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(AlarmServiceServer.this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher_foreground)
                        .setContentTitle(getString(R.string.status))
                        .setContentText("Держите заряд батаери > 40%")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(contentIntent)
                        .setOngoing(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(notificationChannel);
        }
        notificationManager.notify(NOTIFY_ID, builder.build());
    }

    private void startMainThreadServer() {
        mainThreadServer = new MainThreadServer();
        executorService.execute(mainThreadServer);
        countStartMainThreadServer++;
    }

    public void doAlarm(Integer volume){
        if(!AlarmServiceServer.isAlarmOn){
            AlarmServiceServer.isAlarmOn = true;
            AudioManager mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            Integer maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (volume>maxVolume){
                volume = maxVolume;
            } else if (volume<0) {
                volume = 0;
            }
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,volume, 0);
            MediaPlayer mp = MediaPlayer.create(this, R.raw.imperial_march);
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    AlarmServiceServer.isAlarmOn = false;
                }
            });
            mp.start();
        }
    }

    public int tryParseInt(String value, int defaultVal) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}