package com.smallbiz.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.smallbiz.tenant.MemberRole;

/** 签发访问令牌（JWT）与生成刷新令牌。 */
@Service
public class TokenService {

    public static final String CLAIM_TENANT_ID = "tid";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_PLATFORM_ADMIN = "pa";

    private final JwtEncoder encoder;
    private final SecurityProperties props;
    private final SecureRandom random = new SecureRandom();

    public TokenService(JwtEncoder encoder, SecurityProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    /**
     * @param tenantId 当前商户；为空表示尚未选择/创建商户
     * @param role     在当前商户中的角色；tenantId 为空时为空
     */
    public String createAccessToken(Long userId, Long tenantId, MemberRole role, boolean platformAdmin) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("smallbiz")
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()));
        if (tenantId != null) {
            claims.claim(CLAIM_TENANT_ID, tenantId);
            claims.claim(CLAIM_ROLE, role.name());
        }
        if (platformAdmin) {
            claims.claim(CLAIM_PLATFORM_ADMIN, true);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    public long accessTokenTtlSeconds() {
        return props.accessTokenTtl().toSeconds();
    }

    /** 生成刷新令牌原文（256 位随机数）。 */
    public String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
