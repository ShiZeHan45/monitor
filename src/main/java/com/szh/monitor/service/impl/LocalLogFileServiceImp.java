package com.szh.monitor.service.impl;

import com.szh.monitor.config.LocalLogConfig;
import com.szh.monitor.service.WatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class LocalLogFileServiceImp implements WatchService {
    @Autowired
    private LocalLogConfig localLogConfig;

    @Autowired
    private SendDispatchService sendDispatchService;

    private final Map<String, Long> recentErrors = new ConcurrentHashMap<>();
    @Override
    public void watchFile() {
        if(!localLogConfig.isEnabled()){
            return;
        }
        File logFile = new File(localLogConfig.getErrorLogPath());
        if (!logFile.exists()) {
            System.err.println("❌ 日志文件不存在: " + localLogConfig.getErrorLogPath());
            return;
        }

        System.out.println("📡 开始监听日志文件: " + localLogConfig.getErrorLogPath());

        Pattern keywordPattern = Pattern.compile(String.join("|", localLogConfig.getKeywords()), Pattern.CASE_INSENSITIVE);
        final int captureLimit = localLogConfig.getContextLines(); // 匹配后向下截取行数

        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long filePointer = raf.length(); // 从文件末尾开始
            Path path = logFile.toPath();
            java.nio.file.WatchService watcher = FileSystems.getDefault().newWatchService();
            path.getParent().register(watcher, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);

            StringBuilder buffer = new StringBuilder();
            boolean capturing = false;
            int capturedLines = 0;

            while (true) {
                WatchKey key = watcher.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    String changed = event.context().toString();
                    if ((kind == StandardWatchEventKinds.ENTRY_MODIFY || kind == StandardWatchEventKinds.ENTRY_CREATE)
                            && changed.equals(logFile.getName())) {

                        long newLength = raf.length();

                        // 文件被截断（可能是 logrotate）
                        if (newLength < filePointer) {
                            filePointer = 0;
                            raf.seek(0);
                        } else {
                            raf.seek(filePointer);
                        }

                        String line;

                        while ((line = raf.readLine()) != null) {
                            String decodedLine = new String(line.getBytes("ISO-8859-1"), "UTF-8");
                            if (keywordPattern.matcher(decodedLine).find()&&!capturing) {
                                capturing = true;
                                buffer.append(decodedLine).append("\n");
                                capturedLines = 1;
                            }else if(capturing&&capturedLines <= captureLimit){
                                buffer.append(decodedLine).append("\n");
                            }
                            capturedLines++;
                        }
                        if (capturing) {
                            handleErrorBlock(buffer.toString());
                            capturing = false;
                            buffer.setLength(0);
                            capturedLines = 0;
                        }
                        // 如果文件新增停止但仍在捕获中，继续等下次追加
                        filePointer = raf.getFilePointer();
                    }
                }
                key.reset();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private void handleErrorBlock(String errorBlock) {
        try {
            String key = sha1(errorBlock);
            long now = System.currentTimeMillis();
            long windowMs = localLogConfig.getDedupWindowMinutes() * 60_000L;

            Long lastSent = recentErrors.get(key);
            if (lastSent != null && now - lastSent < windowMs) {
                System.out.println("⚠️ 重复错误（跳过推送）: " + key);
                return;
            }

            recentErrors.put(key, now);

            String content = MessageFormat.format("{0}🚨 **检测到异常日志**\n```\n {1} \n```",localLogConfig.getName(),errorBlock);

            sendDispatchService.sendSimpleMarkDownMsg(content, localLogConfig.getName());

//            System.out.println("✅ 已推送异常日志到企业微信，时间：" + now);
//            System.out.println(content);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String sha1(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(input.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
