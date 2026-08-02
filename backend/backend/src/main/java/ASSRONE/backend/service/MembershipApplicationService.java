package ASSRONE.backend.service;

import ASSRONE.backend.dto.CreateMembershipApplicationRequest;
import ASSRONE.backend.dto.MembershipApplicationDto;
import ASSRONE.backend.mapper.MembershipApplicationMapper;
import ASSRONE.backend.model.MembershipApplication;
import ASSRONE.backend.repository.MembershipApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MembershipApplicationService {

    private final MembershipApplicationRepository repository;
    private final MembershipApplicationMapper mapper;
    private final MembershipEmailService membershipEmailService;

    public MembershipApplicationDto submit(CreateMembershipApplicationRequest request) {
        MembershipApplication entity = mapper.fromCreateRequest(request);
        MembershipApplication saved = repository.save(entity);
        membershipEmailService.sendApplicationNotification(saved);
        return mapper.toDto(saved);
    }

    public List<MembershipApplicationDto> getAll() {
        return repository.findAllByOrderBySubmittedAtDesc()
                .stream()
                .map(mapper::toDto)
                .toList();
    }
}
