package com.example.mpdkiosk;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private ImageView imageView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastFile = "";
    private volatile boolean running;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Immersive fullscreen (hide status bar + nav bar)
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        imageView = new ImageView(this);
        imageView.setBackgroundColor(Color.BLACK);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        setContentView(imageView);

        running = true;
        new Thread(this::pollLoop).start();
    }

    @Override
    protected void onDestroy() {
        running = false;
        super.onDestroy();
    }

    private void pollLoop() {
        while (running) {
            try {
                update();
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void update() throws Exception {
        String host = BuildConfig.MPD_HOST;
        int port = BuildConfig.MPD_PORT;

        try (Socket sock = new Socket(host, port)) {
            sock.setSoTimeout(5000);
            InputStream in = sock.getInputStream();
            OutputStream out = sock.getOutputStream();

            // Read greeting
            readLine(in);

            // Get current song file
            out.write("currentsong\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            String file = null;
            String line;
            while (!(line = readLine(in)).equals("OK")) {
                if (line.startsWith("file: ")) {
                    file = line.substring(6);
                }
            }

            if (file == null) return;

            // Skip if same track
            if (file.equals(lastFile)) return;

            // Fetch album art via readpicture (chunked)
            byte[] imageData = fetchPicture(in, out, file);

            if (imageData != null && imageData.length > 0) {
                lastFile = file;
                Bitmap bmp = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                if (bmp != null) {
                    handler.post(() -> imageView.setImageBitmap(bmp));
                }
            }
        }
    }

    private byte[] fetchPicture(InputStream in, OutputStream out, String file) throws Exception {
        ByteArrayOutputStream picture = new ByteArrayOutputStream();
        int offset = 0;

        while (true) {
            String cmd = "readpicture \"" + file.replace("\\", "\\\\").replace("\"", "\\\"") + "\" " + offset + "\n";
            out.write(cmd.getBytes(StandardCharsets.UTF_8));
            out.flush();

            String line = readLine(in);

            // No picture available
            if (line.equals("OK")) return null;
            if (line.startsWith("ACK")) return null;

            // Parse size: and binary: headers
            int totalSize = 0;
            int binaryLen = 0;
            while (true) {
                if (line.startsWith("size: ")) {
                    totalSize = Integer.parseInt(line.substring(6));
                } else if (line.startsWith("binary: ")) {
                    binaryLen = Integer.parseInt(line.substring(8));
                    break;
                }
                // skip type: line etc
                line = readLine(in);
            }

            // Read binary data
            byte[] buf = new byte[binaryLen];
            int read = 0;
            while (read < binaryLen) {
                int n = in.read(buf, read, binaryLen - read);
                if (n < 0) throw new Exception("EOF");
                read += n;
            }
            picture.write(buf);

            // Read trailing newline + OK
            readLine(in); // empty line after binary
            readLine(in); // OK

            offset += binaryLen;
            if (offset >= totalSize) break;
        }

        return picture.toByteArray();
    }

    private String readLine(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            sb.append((char) c);
        }
        return sb.toString();
    }
}
