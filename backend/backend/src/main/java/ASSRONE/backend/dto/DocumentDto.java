package ASSRONE.backend.dto;


import ASSRONE.backend.model.DocumentVisibility;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDto {
    private Long id;
    private String title;
    private String description;
    private String originalFilename;
    private String contentType;
    private long fileSize;
    private String uploadedBy;
    private DocumentVisibility visibility;
    private LocalDateTime uploadedAt;
}
