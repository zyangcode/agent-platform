package com.ls.agent.web.controller;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.common.response.ApiResponse;
import com.ls.agent.core.skill.api.JarSkillService;
import com.ls.agent.core.skill.api.SkillQueryService;
import com.ls.agent.core.skill.command.RegisterJarSkillCommand;
import com.ls.agent.core.skill.dto.JarSkillRegistrationResult;
import com.ls.agent.core.skill.dto.SkillDTO;
import com.ls.agent.web.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@RestController
public class SkillController {

    private final SkillQueryService skillQueryService;
    private final JarSkillService jarSkillService;
    private final Path jarStorageDir;

    public SkillController(
            SkillQueryService skillQueryService,
            JarSkillService jarSkillService,
            @Value("${skill.jar.storage-dir:./data/jar-skills}") String jarStorageDir
    ) {
        this.skillQueryService = skillQueryService;
        this.jarSkillService = jarSkillService;
        this.jarStorageDir = Path.of(jarStorageDir);
    }

    @GetMapping("/api/skills")
    public ApiResponse<List<SkillDTO>> listSkills(
            CurrentUser currentUser,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "status", required = false) String status
    ) {
        return ApiResponse.success(skillQueryService.listSkills(currentUser.tenantId(), scope, status));
    }

    @PostMapping(value = "/api/skills/jar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<JarSkillRegistrationResult> uploadJarSkill(
            CurrentUser currentUser,
            @RequestParam(name = "scope", defaultValue = "PERSONAL") String scope,
            @RequestPart("jarFile") MultipartFile jarFile,
            @RequestPart("manifest") MultipartFile manifest
    ) {
        if (jarFile == null || jarFile.isEmpty()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Jar file must not be empty");
        }
        if (manifest == null || manifest.isEmpty()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Jar manifest must not be empty");
        }
        try {
            Files.createDirectories(jarStorageDir);
            String fileName = sanitizeFileName(jarFile.getOriginalFilename());
            Path jarPath = jarStorageDir.resolve(UUID.randomUUID() + "-" + fileName).normalize();
            if (!jarPath.startsWith(jarStorageDir.toAbsolutePath().normalize())
                    && !jarPath.startsWith(jarStorageDir.normalize())) {
                throw new BizException(ErrorCode.REQUEST_INVALID, "Invalid jar file name");
            }
            String checksum = copyAndSha256(jarFile, jarPath);
            String manifestJson = new String(manifest.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return ApiResponse.success(jarSkillService.register(new RegisterJarSkillCommand(
                    currentUser.tenantId(),
                    currentUser.userId(),
                    scope,
                    jarPath,
                    fileName,
                    jarFile.getSize(),
                    checksum,
                    manifestJson
            )));
        } catch (IOException ex) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Jar skill upload failed: " + safeMessage(ex));
        }
    }

    private String copyAndSha256(MultipartFile jarFile, Path jarPath) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[8192];
        try (InputStream input = jarFile.getInputStream();
             OutputStream output = Files.newOutputStream(jarPath)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "SHA-256 digest is unavailable");
        }
    }

    private String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "skill.jar";
        }
        String fileName = Path.of(originalFilename).getFileName().toString()
                .replaceAll("[^A-Za-z0-9._-]", "_");
        return fileName.isBlank() ? "skill.jar" : fileName;
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
