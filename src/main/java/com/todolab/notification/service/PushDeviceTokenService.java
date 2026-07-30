package com.todolab.notification.service;

import com.todolab.notification.domain.PushDeviceToken;
import com.todolab.notification.dto.PushDeviceTokenRequest;
import com.todolab.notification.dto.PushDeviceTokenResponse;
import com.todolab.notification.repository.PushDeviceTokenRepository;
import com.todolab.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PushDeviceTokenService {

    private final PushDeviceTokenRepository pushDeviceTokenRepository;

    @Transactional
    public PushDeviceTokenResponse registerForOwner(PushDeviceTokenRequest request, User owner) {
        Long ownerId = ownerId(owner);
        PushDeviceToken token = pushDeviceTokenRepository.findByOwnerIdAndDeviceToken(ownerId, request.deviceToken().trim())
                .orElseGet(() -> new PushDeviceToken(
                        owner,
                        request.platform(),
                        request.deviceToken(),
                        request.appVersion(),
                        request.deviceName()
                ));
        token.register(request.platform(), request.deviceToken(), request.appVersion(), request.deviceName());
        return PushDeviceTokenResponse.from(pushDeviceTokenRepository.save(token));
    }

    @Transactional(readOnly = true)
    public List<PushDeviceTokenResponse> getActiveTokensForOwner(User owner) {
        return pushDeviceTokenRepository.findByOwnerIdAndActiveTrueOrderByLastRegisteredAtDescIdDesc(ownerId(owner)).stream()
                .map(PushDeviceTokenResponse::from)
                .toList();
    }

    @Transactional
    public void deactivateForOwner(Long id, User owner) {
        pushDeviceTokenRepository.findByIdAndOwnerId(id, ownerId(owner))
                .ifPresent(PushDeviceToken::deactivate);
    }

    private Long ownerId(User owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner는 영속화된 사용자여야 합니다.");
        }
        return owner.getId();
    }
}
