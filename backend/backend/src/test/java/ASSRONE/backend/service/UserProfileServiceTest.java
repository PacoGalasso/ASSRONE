package ASSRONE.backend.service;

import ASSRONE.backend.dto.ChangePasswordRequest;
import ASSRONE.backend.dto.UpdateProfileRequest;
import ASSRONE.backend.exception.InvalidAvatarException;
import ASSRONE.backend.exception.InvalidPasswordException;
import ASSRONE.backend.exception.UserAlreadyExistsException;
import ASSRONE.backend.mapper.UserProfileMapper;
import ASSRONE.backend.model.User;
import ASSRONE.backend.repository.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserInfoRepository userRepository;

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private RefreshTokenService refreshTokenService;

    @TempDir
    private Path uploadDir;

    private UserProfileService service;

    @BeforeEach
    void setUp() {
        refreshTokenService = mock(RefreshTokenService.class);
        service = new UserProfileService(userRepository, userProfileMapper, passwordEncoder, new AvatarImageInspector(), refreshTokenService);
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());
    }

    private User existingUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("membre@assrone.ch");
        user.setPassword("mot-de-passe-actuel-hache");
        return user;
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream stream = UserProfileServiceTest.class.getResourceAsStream("/avatars/" + name)) {
            if (stream == null) {
                throw new IllegalStateException("Fixture manquante : " + name);
            }
            return stream.readAllBytes();
        }
    }

    private Path avatarsDir() {
        return uploadDir.resolve("avatars");
    }

    // ===== Mot de passe (non-régression LOT 3b) =====

    @Test
    void motDePasseActuelIncorrectLeveInvalidPasswordException() {
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mauvais-mot-de-passe", user.getPassword())).thenReturn(false);
        ChangePasswordRequest request = new ChangePasswordRequest("mauvais-mot-de-passe", "nouveauMotDePasse123");

        assertThatThrownBy(() -> service.changePassword("membre@assrone.ch", request))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("Le mot de passe actuel est incorrect");
    }

    @Test
    void motDePasseActuelIncorrectNeSauvegardeRien() {
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mauvais-mot-de-passe", user.getPassword())).thenReturn(false);
        ChangePasswordRequest request = new ChangePasswordRequest("mauvais-mot-de-passe", "nouveauMotDePasse123");

        assertThatThrownBy(() -> service.changePassword("membre@assrone.ch", request))
                .isInstanceOf(InvalidPasswordException.class);

        verify(userRepository, Mockito.never()).save(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void motDePasseActuelCorrectEncodeEtSauvegardeLeNouveauMotDePasse() {
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bon-mot-de-passe", user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("nouveauMotDePasse123")).thenReturn("nouveau-mot-de-passe-hache");
        ChangePasswordRequest request = new ChangePasswordRequest("bon-mot-de-passe", "nouveauMotDePasse123");

        service.changePassword("membre@assrone.ch", request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("nouveau-mot-de-passe-hache");
    }

    @Test
    void motDePasseActuelCorrectRevoqueTousLesRefreshTokensDeLUtilisateur() {
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bon-mot-de-passe", user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("nouveauMotDePasse123")).thenReturn("nouveau-mot-de-passe-hache");
        ChangePasswordRequest request = new ChangePasswordRequest("bon-mot-de-passe", "nouveauMotDePasse123");

        service.changePassword("membre@assrone.ch", request);

        verify(refreshTokenService).revokeAllForUser(1L);
    }

    // ===== Mise à jour du profil =====

    @Test
    void miseAJourDuProfilNormaliseLEmailCommeALInscriptionEtAuLogin() {
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .username("membre")
                .firstName("Jean")
                .lastName("Dupont")
                .email("  Membre@ASSRONE.ch  ")
                .build();

        service.updateProfile("membre@assrone.ch", request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("membre@assrone.ch");
    }

    @Test
    void miseAJourDuProfilAvecUnEmailDejaUtiliseParUnAutreCompteEstRefusee() {
        User user = existingUser();
        User autreCompte = new User();
        autreCompte.setId(2L);
        autreCompte.setEmail("dejapris@assrone.ch");
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("dejapris@assrone.ch")).thenReturn(Optional.of(autreCompte));
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .username("membre")
                .firstName("Jean")
                .lastName("Dupont")
                .email("dejapris@assrone.ch")
                .build();

        assertThatThrownBy(() -> service.updateProfile("membre@assrone.ch", request))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void miseAJourDuProfilSansChangementDEmailNeDeclencheAucuneVerificationDeDoublon() {
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .username("nouveau-pseudo")
                .firstName("Jean")
                .lastName("Dupont")
                .email("membre@assrone.ch")
                .build();

        service.updateProfile("membre@assrone.ch", request);

        // Un seul appel à findByEmail : la recherche de l'utilisateur courant.
        // Aucune recherche de doublon puisque l'email normalisé est identique.
        verify(userRepository, Mockito.times(1)).findByEmail("membre@assrone.ch");
        verify(userRepository).save(any());
    }

    // ===== Upload d'avatar =====

    @Test
    void uploadAvatarAvecUnContenuInvalideNEcritAucunFichier() throws IOException {
        // L'inspection précède la récupération de l'utilisateur : aucun stub sur
        // userRepository.findByEmail n'est nécessaire, la méthode n'est jamais atteinte.
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", fixture("not-an-image.jpg"));

        assertThatThrownBy(() -> service.uploadAvatar("membre@assrone.ch", file))
                .isInstanceOf(InvalidAvatarException.class);

        assertThat(Files.exists(avatarsDir())).isFalse();
        verify(userRepository, Mockito.never()).save(any());
    }

    @Test
    void uploadAvatarIgnoreLeNomOriginalPourLeNomPhysique() throws IOException {
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../../etc/passwd.jpg", "image/jpeg", fixture("small-valid.jpg"));

        service.uploadAvatar("membre@assrone.ch", file);

        assertThat(user.getAvatarFilename())
                .doesNotContain("etc")
                .doesNotContain("passwd")
                .doesNotContain("..")
                .endsWith(".jpg");
    }

    @Test
    void uploadAvatarIgnoreLeTypeMimeMultipartFalsifie() throws IOException {
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "application/pdf", fixture("small-valid.jpg"));

        service.uploadAvatar("membre@assrone.ch", file);

        assertThat(user.getAvatarFilename()).endsWith(".jpg");
    }

    @Test
    void uploadAvatarStockeLExtensionIssueDuFormatDetecte() throws IOException {
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", fixture("small-valid.png"));

        service.uploadAvatar("membre@assrone.ch", file);

        assertThat(user.getAvatarFilename()).endsWith(".png");
    }

    @Test
    void uploadAvatarGenereUnNomPhysiqueAuFormatUuid() throws IOException {
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", fixture("small-valid.jpg"));

        service.uploadAvatar("membre@assrone.ch", file);

        assertThat(user.getAvatarFilename())
                .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg$");
    }

    @Test
    void uploadAvatarEcritLeFichierSousLaRacineAutorisee() throws IOException {
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", fixture("small-valid.jpg"));

        service.uploadAvatar("membre@assrone.ch", file);

        Path stored = avatarsDir().resolve(user.getAvatarFilename());
        assertThat(stored).exists();
        assertThat(stored.normalize()).startsWith(avatarsDir().normalize());
    }

    @Test
    void uploadAvatarCreeLeRepertoireAvatarsSiAbsent() throws IOException {
        assertThat(Files.exists(avatarsDir())).isFalse();
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", fixture("small-valid.jpg"));

        service.uploadAvatar("membre@assrone.ch", file);

        assertThat(Files.isDirectory(avatarsDir())).isTrue();
    }

    @Test
    void uploadAvatarAvecValidationEnEchecLaisseLAncienAvatarIntact() throws IOException {
        // L'inspection précède la récupération de l'utilisateur : aucun stub sur
        // userRepository.findByEmail n'est nécessaire, la méthode n'est jamais atteinte.
        Files.createDirectories(avatarsDir());
        Path ancien = avatarsDir().resolve("ancien.jpg");
        Files.write(ancien, fixture("small-valid.jpg"));
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", fixture("not-an-image.jpg"));

        assertThatThrownBy(() -> service.uploadAvatar("membre@assrone.ch", file))
                .isInstanceOf(InvalidAvatarException.class);

        assertThat(ancien).exists();
    }

    @Test
    void uploadAvatarAvecEchecDEcritureNAppelleJamaisLaSauvegardeDb() throws IOException {
        // Un fichier régulier existe déjà là où le répertoire "avatars" devrait être créé :
        // Files.createDirectories échoue alors réellement, sans mock ni dépendance aux permissions OS.
        Files.write(avatarsDir(), fixture("small-valid.jpg"));

        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", fixture("small-valid.jpg"));

        assertThatThrownBy(() -> service.uploadAvatar("membre@assrone.ch", file))
                .isInstanceOf(IOException.class);

        verify(userRepository, Mockito.never()).save(any());
    }

    @Test
    void uploadAvatarReussiSupprimeLAncienFichierApresSauvegardeReussie() throws IOException {
        Files.createDirectories(avatarsDir());
        Path ancien = avatarsDir().resolve("ancien.jpg");
        Files.write(ancien, fixture("small-valid.jpg"));

        User user = existingUser();
        user.setAvatarFilename("ancien.jpg");
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", fixture("small-valid.png"));

        service.uploadAvatar("membre@assrone.ch", file);

        assertThat(ancien).doesNotExist();
        verify(userRepository).save(user);
    }

    @Test
    void uploadAvatarAvecEchecDeSauvegardeSupprimeLeNouveauFichierEtConserveLAncien() throws IOException {
        Files.createDirectories(avatarsDir());
        Path ancien = avatarsDir().resolve("ancien.jpg");
        Files.write(ancien, fixture("small-valid.jpg"));

        User user = existingUser();
        user.setAvatarFilename("ancien.jpg");
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        DataAccessResourceFailureException dbFailure = new DataAccessResourceFailureException("base indisponible");
        when(userRepository.save(user)).thenThrow(dbFailure);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", fixture("small-valid.jpg"));

        assertThatThrownBy(() -> service.uploadAvatar("membre@assrone.ch", file))
                .isSameAs(dbFailure);

        assertThat(ancien).exists();
        assertThat(user.getAvatarFilename()).isEqualTo("ancien.jpg");
        try (var files = Files.list(avatarsDir())) {
            assertThat(files).containsExactly(ancien);
        }
    }

    @Test
    void uploadAvatarAvecEchecDeSauvegardeParUneExceptionRuntimeNonDataAccessSupprimeLeNouveauFichierEtConserveLAncien() throws IOException {
        Files.createDirectories(avatarsDir());
        Path ancien = avatarsDir().resolve("ancien.jpg");
        Files.write(ancien, fixture("small-valid.jpg"));

        User user = existingUser();
        user.setAvatarFilename("ancien.jpg");
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        RuntimeException dbFailure = new RuntimeException("erreur inattendue non liée à Spring Data");
        when(userRepository.save(user)).thenThrow(dbFailure);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", fixture("small-valid.jpg"));

        assertThatThrownBy(() -> service.uploadAvatar("membre@assrone.ch", file))
                .isSameAs(dbFailure);

        assertThat(ancien).exists();
        assertThat(user.getAvatarFilename()).isEqualTo("ancien.jpg");
        try (var files = Files.list(avatarsDir())) {
            assertThat(files).containsExactly(ancien);
        }
    }

    @Test
    void uploadAvatarNeModifiePasLesAutresProprietesUtilisateur() throws IOException {
        User user = existingUser();
        when(userRepository.findByEmail("membre@assrone.ch")).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", fixture("small-valid.jpg"));

        service.uploadAvatar("membre@assrone.ch", file);

        assertThat(user.getEmail()).isEqualTo("membre@assrone.ch");
        assertThat(user.getPassword()).isEqualTo("mot-de-passe-actuel-hache");
    }
}
