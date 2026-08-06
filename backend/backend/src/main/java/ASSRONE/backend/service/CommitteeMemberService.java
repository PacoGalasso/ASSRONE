package ASSRONE.backend.service;

import ASSRONE.backend.audit.SecurityAuditService;
import ASSRONE.backend.audit.SecurityEventResult;
import ASSRONE.backend.audit.SecurityEventType;
import ASSRONE.backend.dto.CommitteeMemberDto;
import ASSRONE.backend.dto.CreateCommitteeMemberRequest;
import ASSRONE.backend.dto.UpdateCommitteeMemberRequest;
import ASSRONE.backend.exception.ResourceNotFoundException;
import ASSRONE.backend.mapper.CommitteeMemberMapper;
import ASSRONE.backend.model.CommitteeMember;
import ASSRONE.backend.repository.CommitteeMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommitteeMemberService {

    private final CommitteeMemberRepository committeeMemberRepository;
    private final CommitteeMemberMapper committeeMemberMapper;
    private final AvatarImageInspector avatarImageInspector;
    private final SecurityAuditService securityAuditService;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public List<CommitteeMemberDto> getPublicMembers() {
        return committeeMemberRepository.findByActiveTrueOrderByDisplayOrderAscLastNameAsc()
                .stream()
                .map(committeeMemberMapper::toDto)
                .toList();
    }

    public List<CommitteeMemberDto> getAllForAdmin() {
        return committeeMemberRepository.findAllByOrderByDisplayOrderAscLastNameAsc()
                .stream()
                .map(committeeMemberMapper::toDto)
                .toList();
    }

    public CommitteeMemberDto create(CreateCommitteeMemberRequest request) {
        CommitteeMember member = committeeMemberMapper.fromCreateRequest(request);
        trim(member);
        CommitteeMember saved = committeeMemberRepository.save(member);
        recordAudit(SecurityEventType.COMMITTEE_MEMBER_CREATED, saved.getId());
        return committeeMemberMapper.toDto(saved);
    }

    public CommitteeMemberDto update(Long id, UpdateCommitteeMemberRequest request) {
        CommitteeMember member = getByIdOrThrow(id);
        committeeMemberMapper.updateEntityFromRequest(request, member);
        trim(member);
        CommitteeMember saved = committeeMemberRepository.save(member);
        recordAudit(SecurityEventType.COMMITTEE_MEMBER_UPDATED, saved.getId());
        return committeeMemberMapper.toDto(saved);
    }

    public void delete(Long id) {
        CommitteeMember member = getByIdOrThrow(id);
        String photoFilename = member.getPhotoFilename();
        committeeMemberRepository.delete(member);
        if (photoFilename != null) {
            deleteQuietly(photoDirectory().resolve(photoFilename));
        }
        recordAudit(SecurityEventType.COMMITTEE_MEMBER_DELETED, id);
    }

    public CommitteeMemberDto uploadPhoto(Long id, MultipartFile file) throws IOException {
        CommitteeMember member = getByIdOrThrow(id);
        ValidatedAvatar validated = avatarImageInspector.inspect(file.getBytes());

        String previousPhotoFilename = member.getPhotoFilename();
        String storedFilename = UUID.randomUUID() + validated.format().extension();
        Path target = resolvePhotoPath(storedFilename);
        Files.write(target, validated.content());

        member.setPhotoFilename(storedFilename);
        try {
            committeeMemberRepository.save(member);
        } catch (RuntimeException ex) {
            deleteQuietly(target);
            member.setPhotoFilename(previousPhotoFilename);
            throw ex;
        }

        if (previousPhotoFilename != null) {
            deleteQuietly(photoDirectory().resolve(previousPhotoFilename));
        }

        recordAudit(SecurityEventType.COMMITTEE_MEMBER_PHOTO_CHANGED, id);
        return committeeMemberMapper.toDto(member);
    }

    public CommitteeMemberDto deletePhoto(Long id) {
        CommitteeMember member = getByIdOrThrow(id);
        String previousPhotoFilename = member.getPhotoFilename();
        member.setPhotoFilename(null);
        CommitteeMember saved = committeeMemberRepository.save(member);

        if (previousPhotoFilename != null) {
            deleteQuietly(photoDirectory().resolve(previousPhotoFilename));
        }

        recordAudit(SecurityEventType.COMMITTEE_MEMBER_PHOTO_CHANGED, id);
        return committeeMemberMapper.toDto(saved);
    }

    private void recordAudit(SecurityEventType eventType, Long targetId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actorId = authentication != null ? SecurityAuditService.maskEmail(authentication.getName()) : "-";
        securityAuditService.record(eventType, SecurityEventResult.SUCCESS,
                actorId, null, "committeeMember", String.valueOf(targetId), null);
    }

    public Resource loadPhoto(Long id, Authentication authentication) throws MalformedURLException {
        CommitteeMember member = committeeMemberRepository.findById(id).orElse(null);
        if (member == null || member.getPhotoFilename() == null) {
            return null;
        }
        if (!member.isActive() && !isAdmin(authentication)) {
            return null;
        }
        Path path = photoDirectory().resolve(member.getPhotoFilename());
        return new UrlResource(path.toUri());
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private void trim(CommitteeMember member) {
        member.setFirstName(member.getFirstName().trim());
        member.setLastName(member.getLastName().trim());
        member.setRole(member.getRole().trim());
        if (member.getDescription() != null) {
            String trimmed = member.getDescription().trim();
            member.setDescription(trimmed.isEmpty() ? null : trimmed);
        }
    }

    private CommitteeMember getByIdOrThrow(Long id) {
        return committeeMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membre du comité introuvable : " + id));
    }

    private Path photoDirectory() {
        Path root = Paths.get(uploadDir, "committee-photos").toAbsolutePath().normalize();
        return root;
    }

    private Path resolvePhotoPath(String storedFilename) throws IOException {
        Path root = photoDirectory();
        if (!Files.exists(root)) {
            Files.createDirectories(root);
        }
        Path target = root.resolve(storedFilename).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("Chemin de photo résolu hors du répertoire autorisé.");
        }
        return target;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Échec du nettoyage d'un fichier photo de membre du comité.");
        }
    }
}
