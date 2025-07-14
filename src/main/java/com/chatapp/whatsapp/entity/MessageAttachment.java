package com.chatapp.whatsapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;


import java.time.LocalDateTime;

@Entity
@Table(name = "message_attachments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "thumbnail_path")
    private String thumbnailPath;

    @Column(name = "duration") // For video/audio files
    private Long duration;

    @Column(name = "width") // For images/videos
    private Integer width;

    @Column(name = "height") // For images/videos
    private Integer height;

    @Column(name = "is_compressed")
    @Builder.Default
    private Boolean isCompressed = false;

    @Column(name = "compression_quality")
    private Integer compressionQuality;

    // Helper methods
    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }

    public boolean isVideo() {
        return mimeType != null && mimeType.startsWith("video/");
    }

    public boolean isAudio() {
        return mimeType != null && mimeType.startsWith("audio/");
    }

    public boolean isDocument() {
        return mimeType != null &&
                !mimeType.startsWith("image/") &&
                !mimeType.startsWith("video/") &&
                !mimeType.startsWith("audio/");
    }

    public String getFileExtension() {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
        return "";
    }

    public String getFileSizeFormatted() {
        if (fileSize == null) return "0 B";

        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        double size = fileSize;

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        return String.format("%.1f %s", size, units[unitIndex]);
    }

    public boolean hasThumbnail() {
        return thumbnailPath != null && !thumbnailPath.isEmpty();
    }

    public boolean hasDimensions() {
        return width != null && height != null && width > 0 && height > 0;
    }

    public boolean hasDuration() {
        return duration != null && duration > 0;
    }

    public String getDurationFormatted() {
        if (duration == null || duration <= 0) return "00:00";

        long minutes = duration / 60;
        long seconds = duration % 60;

        return String.format("%02d:%02d", minutes, seconds);
    }

    // Convenience methods for messageId
    public Long getMessageId() {
        return message != null ? message.getId() : null;
    }

    public void setMessageId(Long messageId) {
        if (this.message == null) {
            this.message = new Message();
        }
        this.message.setId(messageId);
    }
}
