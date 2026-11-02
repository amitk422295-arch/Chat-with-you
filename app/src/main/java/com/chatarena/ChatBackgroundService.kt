package com.chatarena

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ChatBackgroundService : Service() {

    private val CHANNEL_ID = "ChatArenaBackgroundChannel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. फोरग्राउंड सर्विस शुरू करो (नोटिफिकेशन के साथ)
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // 2. ग्लोबल चैट मैनेजर को शुरू करो (WebSocket कनेक्ट रहेगा)
        GlobalChatManager.init(applicationContext)

        // 3. START_STICKY का मतलब: अगर फोन इसे मार भी दे, तो इसे खुद से दोबारा चालू कर दे
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "ChatArena Background Service",
                NotificationManager.IMPORTANCE_LOW // Low rakha hai taaki baar-baar awaaz na aaye
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        // नोटिफिकेशन पर क्लिक करने से ऐप खुलेगा
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // छोटा आइकॉन (आप अपना आइकॉन भी लगा सकते हैं)
        val icon = android.R.drawable.ic_dialog_info 

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChatArena is active")
            .setContentText("Keeping you connected to your chats.")
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // यूजर इसे स्वाइप करके बंद नहीं कर सकता
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
