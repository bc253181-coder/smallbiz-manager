package com.smallbiz.billing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.smallbiz.catalog.ImageStorage;
import com.smallbiz.config.PlatformProperties;

/**
 * 首次启动：如果收款方式还没有收款码图片，从 assets/payment 导入。
 * 之后平台管理员可在后台随时更换。
 */
@Component
public class PaymentMethodBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentMethodBootstrap.class);
    private static final Map<String, String> FILES = Map.of(
            "WECHAT_PAY", "wechat-pay.jpg",
            "TNG_DUITNOW", "tng-duitnow.jpg");

    private final PaymentMethodRepository methods;
    private final ImageStorage storage;
    private final PlatformProperties platform;
    private final TransactionTemplate tx;

    public PaymentMethodBootstrap(PaymentMethodRepository methods, ImageStorage storage, PlatformProperties platform,
            TransactionTemplate tx) {
        this.methods = methods;
        this.storage = storage;
        this.platform = platform;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (platform.paymentAssetsDir() == null) {
            return;
        }
        Path dir = Path.of(platform.paymentAssetsDir());
        tx.executeWithoutResult(s -> methods.findAll().forEach(m -> {
            String file = FILES.get(m.getCode());
            if (m.getQrImagePath() == null && file != null && Files.exists(dir.resolve(file))) {
                m.setQrImagePath(storage.importPublic("platform", dir.resolve(file)));
                log.info("Imported QR code for payment method {}", m.getCode());
            }
        }));
    }
}
