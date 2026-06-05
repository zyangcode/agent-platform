package com.ls.agent.core.skill.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.skill.api.JarSkillService;
import com.ls.agent.core.skill.command.RegisterJarSkillCommand;
import com.ls.agent.core.skill.dto.JarSkillRegistrationResult;
import com.ls.agent.core.skill.entity.SkillArtifactEntity;
import com.ls.agent.core.skill.entity.SkillEntity;
import com.ls.agent.core.skill.entity.SkillVersionEntity;
import com.ls.agent.core.skill.mapper.SkillArtifactMapper;
import com.ls.agent.core.skill.mapper.SkillMapper;
import com.ls.agent.core.skill.mapper.SkillVersionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DefaultJarSkillService implements JarSkillService {

    private static final String TYPE_JAR = "JAR";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String VERSION_READY = "READY";

    private final SkillMapper skillMapper;
    private final SkillVersionMapper skillVersionMapper;
    private final SkillArtifactMapper skillArtifactMapper;
    private final ObjectMapper objectMapper;
    private final JarSkillLoader jarSkillLoader;

    @Autowired
    public DefaultJarSkillService(
            SkillMapper skillMapper,
            SkillVersionMapper skillVersionMapper,
            SkillArtifactMapper skillArtifactMapper,
            ObjectMapper objectMapper,
            JarSkillLoader jarSkillLoader
    ) {
        this.skillMapper = skillMapper;
        this.skillVersionMapper = skillVersionMapper;
        this.skillArtifactMapper = skillArtifactMapper;
        this.objectMapper = objectMapper;
        this.jarSkillLoader = jarSkillLoader;
    }

    public DefaultJarSkillService(
            SkillMapper skillMapper,
            SkillVersionMapper skillVersionMapper,
            SkillArtifactMapper skillArtifactMapper,
            ObjectMapper objectMapper
    ) {
        this.skillMapper = skillMapper;
        this.skillVersionMapper = skillVersionMapper;
        this.skillArtifactMapper = skillArtifactMapper;
        this.objectMapper = objectMapper;
        this.jarSkillLoader = null;
    }

    @Override
    public JarSkillRegistrationResult register(RegisterJarSkillCommand command) {
        JsonNode manifest = readManifest(command.manifestJson());
        String code = requiredText(manifest, "code");
        ensureCodeAvailable(command.tenantId(), code);

        Validation validation = validate(manifest);
        SkillEntity skill = createSkill(command, manifest, code, validation);
        skillMapper.insert(skill);

        SkillVersionEntity version = createVersion(skill.getId(), manifest, command.checksum(), validation);
        skillVersionMapper.insert(version);

        if (validation.valid()) {
            SkillArtifactEntity artifact = createArtifact(version.getId(), command);
            skillArtifactMapper.insert(artifact);
            if (jarSkillLoader != null) {
                try {
                    jarSkillLoader.loadAndRegister(
                            code,
                            command.jarPath(),
                            version.getRuntimeConfig().path("handlerClass").asText()
                    );
                } catch (BizException ex) {
                    String message = ex.getMessage() == null ? "Jar skill load failed" : ex.getMessage();
                    markLoadFailed(skill.getId(), version.getId(), message);
                    return new JarSkillRegistrationResult(
                            skill.getId(),
                            version.getId(),
                            code,
                            STATUS_FAILED,
                            STATUS_FAILED,
                            message
                    );
                }
            }
            SkillEntity update = new SkillEntity();
            update.setId(skill.getId());
            update.setCurrentVersionId(version.getId());
            skillMapper.updateById(update);
        }

        return new JarSkillRegistrationResult(
                skill.getId(),
                version.getId(),
                code,
                skill.getStatus(),
                version.getStatus(),
                validation.message()
        );
    }

    private void markLoadFailed(Long skillId, Long versionId, String message) {
        ObjectNode validationResult = objectMapper.createObjectNode();
        validationResult.put("valid", false);
        validationResult.put("message", message);

        SkillVersionEntity versionUpdate = new SkillVersionEntity();
        versionUpdate.setId(versionId);
        versionUpdate.setStatus(STATUS_FAILED);
        versionUpdate.setValidationResult(validationResult);
        skillVersionMapper.updateById(versionUpdate);

        SkillEntity skillUpdate = new SkillEntity();
        skillUpdate.setId(skillId);
        skillUpdate.setStatus(STATUS_FAILED);
        skillMapper.updateById(skillUpdate);
    }

    private SkillEntity createSkill(
            RegisterJarSkillCommand command,
            JsonNode manifest,
            String code,
            Validation validation
    ) {
        SkillEntity entity = new SkillEntity();
        entity.setTenantId(command.tenantId());
        entity.setOwnerUserId(command.ownerUserId());
        entity.setCode(code);
        entity.setName(textOrDefault(manifest, "name", code));
        entity.setDescription(textOrDefault(manifest, "description", ""));
        entity.setSkillType(TYPE_JAR);
        entity.setScope(command.scope() == null || command.scope().isBlank() ? "PERSONAL" : command.scope());
        entity.setPermissionDeclaration(manifest.path("permissions").isMissingNode()
                ? objectMapper.createObjectNode()
                : manifest.path("permissions"));
        entity.setStatus(validation.valid() ? STATUS_ENABLED : STATUS_FAILED);
        return entity;
    }

    private SkillVersionEntity createVersion(
            Long skillId,
            JsonNode manifest,
            String checksum,
            Validation validation
    ) {
        SkillVersionEntity entity = new SkillVersionEntity();
        entity.setSkillId(skillId);
        entity.setVersion(textOrDefault(manifest, "version", "1.0.0"));
        entity.setParameterSchema(manifest.path("parameterSchema").isMissingNode()
                ? objectMapper.createObjectNode()
                : manifest.path("parameterSchema"));
        entity.setReturnSchema(manifest.path("returnSchema").isMissingNode()
                ? objectMapper.createObjectNode()
                : manifest.path("returnSchema"));
        ObjectNode runtimeConfig = objectMapper.createObjectNode();
        runtimeConfig.put("runtime", "jar");
        runtimeConfig.put("handlerClass", manifest.path("handlerClass").asText(""));
        entity.setRuntimeConfig(runtimeConfig);
        entity.setDependencies(manifest.path("dependencies").isMissingNode()
                ? objectMapper.createArrayNode()
                : manifest.path("dependencies"));
        entity.setChecksum(checksum);
        ObjectNode validationResult = objectMapper.createObjectNode();
        validationResult.put("valid", validation.valid());
        validationResult.put("message", validation.message());
        entity.setValidationResult(validationResult);
        entity.setStatus(validation.valid() ? VERSION_READY : STATUS_FAILED);
        return entity;
    }

    private SkillArtifactEntity createArtifact(Long skillVersionId, RegisterJarSkillCommand command) {
        SkillArtifactEntity entity = new SkillArtifactEntity();
        entity.setSkillVersionId(skillVersionId);
        entity.setArtifactType(TYPE_JAR);
        entity.setStoragePath(command.jarPath().toString().replace('\\', '/'));
        entity.setFileName(command.fileName());
        entity.setSizeBytes(command.sizeBytes());
        entity.setChecksum(command.checksum());
        return entity;
    }

    private JsonNode readManifest(String manifestJson) {
        try {
            return objectMapper.readTree(manifestJson);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Invalid Jar skill manifest JSON");
        }
    }

    private void ensureCodeAvailable(Long tenantId, String code) {
        SkillEntity existing = skillMapper.selectOne(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getTenantId, tenantId)
                .eq(SkillEntity::getCode, code));
        if (existing != null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Skill code already exists: " + code);
        }
    }

    private Validation validate(JsonNode manifest) {
        String handlerClass = textOrDefault(manifest, "handlerClass", "");
        if (handlerClass.isBlank()) {
            return new Validation(false, "Jar skill manifest must declare handlerClass");
        }
        JsonNode parameterSchema = manifest.path("parameterSchema");
        if (parameterSchema.isMissingNode() || !parameterSchema.isObject()) {
            return new Validation(false, "Jar skill manifest must declare parameterSchema object");
        }
        return new Validation(true, "OK");
    }

    private String requiredText(JsonNode manifest, String field) {
        String value = textOrDefault(manifest, field, "");
        if (value.isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Jar skill manifest must declare " + field);
        }
        return value;
    }

    private String textOrDefault(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.asText().isBlank()) {
            return fallback;
        }
        return value.asText();
    }

    private record Validation(boolean valid, String message) {
    }
}
