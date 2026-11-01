package com.zosh.controller;

import com.zosh.modal.PlatformPaymentConfig;
import com.zosh.modal.Store;
import com.zosh.modal.StorePaymentInfo;
import com.zosh.repository.PlatformPaymentConfigRepository;
import com.zosh.repository.StoreRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/payment-gateway")
@RequiredArgsConstructor
public class PaymentGatewayController {

    private final StoreRepository storeRepository;
    private final PlatformPaymentConfigRepository platformPaymentConfigRepository;

    private final Map<String, PaymentSessionDTO> sessions = new ConcurrentHashMap<>();

    private PlatformPaymentConfig getOrCreatePlatformConfig() {
        return platformPaymentConfigRepository.findAll().stream().findFirst().orElseGet(() -> {
            PlatformPaymentConfig def = PlatformPaymentConfig.builder()
                    .acceptMomo(true)
                    .acceptCard(true)
                    .momoPhone("0901234567")
                    .momoOwnerName("POS PRO ENTERPRISE SAAS")
                    .momoQrImage("")
                    .cardBankName("Vietcombank - Visa")
                    .cardNumber("4221567890123456")
                    .cardHolderName("POS PRO ENTERPRISE SAAS")
                    .cardExpiry("12/29")
                    .updatedAt(LocalDateTime.now())
                    .build();
            return platformPaymentConfigRepository.save(def);
        });
    }

    @GetMapping("/platform-config")
    public ResponseEntity<PlatformPaymentConfig> getPlatformPaymentConfig() {
        return ResponseEntity.ok(getOrCreatePlatformConfig());
    }

    @PutMapping("/platform-config")
    public ResponseEntity<PlatformPaymentConfig> updatePlatformPaymentConfig(
            @RequestBody PlatformPaymentConfig payload
    ) {
        PlatformPaymentConfig cfg = getOrCreatePlatformConfig();
        if (payload.getAcceptMomo() != null) cfg.setAcceptMomo(payload.getAcceptMomo());
        if (payload.getAcceptCard() != null) cfg.setAcceptCard(payload.getAcceptCard());
        if (payload.getMomoPhone() != null) cfg.setMomoPhone(payload.getMomoPhone().trim());
        if (payload.getMomoOwnerName() != null) cfg.setMomoOwnerName(payload.getMomoOwnerName().trim());
        if (payload.getMomoQrImage() != null) cfg.setMomoQrImage(payload.getMomoQrImage().trim());
        if (payload.getCardBankName() != null) cfg.setCardBankName(payload.getCardBankName().trim());
        if (payload.getCardNumber() != null) cfg.setCardNumber(payload.getCardNumber().trim());
        if (payload.getCardHolderName() != null) cfg.setCardHolderName(payload.getCardHolderName().trim());
        if (payload.getCardExpiry() != null) cfg.setCardExpiry(payload.getCardExpiry().trim());
        cfg.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(platformPaymentConfigRepository.save(cfg));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSessionDTO {
        private String sessionId;
        private Long storeId;
        private String storeName;
        private Double amount;
        private String method; // MOMO, CARD
        private String status; // PENDING, PROCESSING, SUCCESS, FAILED
        private String referenceCode;
        private String transactionId;
        private String providerName;
        private String providerMessage;
        private String recipientPhone;
        private String recipientName;
        private String bankBin;
        private String bankName;
        private String bankAccount;
        private String vietQrImageUrl;
        private String mobileCheckoutUrl;
        private String qrImageForMobileCheckout;
        private String maskedCardNumber;
        private String cardBrand;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime paidAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSessionRequest {
        private Long storeId;
        private String storeName;
        private Double amount;
        private String method; // MOMO or CARD
        private String description;
        private String recipientPhone;
        private String recipientName;
        private String bankName;
        private String bankAccount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebhookRequest {
        private String status; // SUCCESS or FAILED
        private String providerName;
        private String transactionId;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardAuthorizeRequest {
        private String cardNumber;
        private String expiry; // MM/YY
        private String cvv;
        private String cardHolder;
    }

    @PostMapping("/sessions")
    public ResponseEntity<PaymentSessionDTO> createSession(@RequestBody CreateSessionRequest req) {
        String sessionId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String refCode = "POS" + (System.currentTimeMillis() % 1000000);

        String storeName = req.getStoreName() != null && !req.getStoreName().isBlank()
                ? req.getStoreName() : "POS Pro Store";
        String momoPhone = req.getRecipientPhone();
        String momoName = req.getRecipientName();
        String bankName = req.getBankName();
        String bankAccount = req.getBankAccount();

        if (req.getStoreId() != null) {
            Store store = storeRepository.findById(req.getStoreId()).orElse(null);
            if (store != null) {
                if (store.getBrand() != null) {
                    storeName = store.getBrand();
                }
                StorePaymentInfo info = store.getPaymentInfo();
                if (info != null) {
                    if ((momoPhone == null || momoPhone.isBlank()) && info.getMomoPhone() != null) {
                        momoPhone = info.getMomoPhone();
                    }
                    if ((momoName == null || momoName.isBlank()) && info.getMomoOwnerName() != null) {
                        momoName = info.getMomoOwnerName();
                    }
                    if ((bankName == null || bankName.isBlank()) && info.getCardBankName() != null) {
                        bankName = info.getCardBankName();
                    }
                    if ((bankAccount == null || bankAccount.isBlank()) && info.getCardNumber() != null) {
                        bankAccount = info.getCardNumber();
                    }
                    if ((momoName == null || momoName.isBlank()) && info.getCardHolderName() != null) {
                        momoName = info.getCardHolderName();
                    }
                }
            }
        } else {
            // Khi thanh toán gói cước SaaS (Store trả cho POS Admin / Super Admin)
            PlatformPaymentConfig platformCfg = getOrCreatePlatformConfig();
            if ((momoPhone == null || momoPhone.isBlank()) && platformCfg.getMomoPhone() != null) {
                momoPhone = platformCfg.getMomoPhone();
            }
            if ((momoName == null || momoName.isBlank()) && platformCfg.getMomoOwnerName() != null) {
                momoName = platformCfg.getMomoOwnerName();
            }
            if ((bankName == null || bankName.isBlank()) && platformCfg.getCardBankName() != null) {
                bankName = platformCfg.getCardBankName();
            }
            if ((bankAccount == null || bankAccount.isBlank()) && platformCfg.getCardNumber() != null) {
                bankAccount = platformCfg.getCardNumber();
            }
            if ((momoName == null || momoName.isBlank()) && platformCfg.getCardHolderName() != null) {
                momoName = platformCfg.getCardHolderName();
            }
        }

        if (momoPhone == null || momoPhone.isBlank()) {
            momoPhone = "0901234567";
        }
        if (momoName == null || momoName.isBlank()) {
            momoName = storeName.toUpperCase();
        }
        if (bankName == null || bankName.isBlank()) {
            bankName = "MB Bank";
        }

        String accountForVietQr = (bankAccount != null && !bankAccount.isBlank())
                ? bankAccount.replaceAll("\\s+", "")
                : momoPhone.replaceAll("\\s+", "");
        String bankBin = resolveBankBin(bankName);
        long amountVnd = req.getAmount() != null ? Math.max(1000L, Math.round(req.getAmount())) : 10000L;

        String encodedAddInfo = URLEncoder.encode(refCode + " Thanh toan " + storeName, StandardCharsets.UTF_8);
        String encodedAccountName = URLEncoder.encode(momoName, StandardCharsets.UTF_8);

        // Chuẩn EMVCo VietQR chính thức (quét hợp lệ 100% trên mọi app Ngân hàng & MoMo)
        String vietQrImageUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-compact2.png?amount=%d&addInfo=%s&accountName=%s",
                bankBin, accountForVietQr, amountVnd, encodedAddInfo, encodedAccountName
        );

        String lanIp = detectLanIp();
        String mobileCheckoutUrl = String.format("http://%s:5000/api/payment-gateway/checkout/%s", lanIp, sessionId);
        String qrImageForMobileCheckout = "https://api.qrserver.com/v1/create-qr-code/?size=280x280&margin=8&data="
                + URLEncoder.encode(mobileCheckoutUrl, StandardCharsets.UTF_8);

        PaymentSessionDTO session = PaymentSessionDTO.builder()
                .sessionId(sessionId)
                .storeId(req.getStoreId())
                .storeName(storeName)
                .amount((double) amountVnd)
                .method(req.getMethod() != null ? req.getMethod().toUpperCase() : "MOMO")
                .status("PENDING")
                .referenceCode(refCode)
                .providerName("MOMO".equalsIgnoreCase(req.getMethod()) ? "MoMo / Napas247 VietQR" : "Visa / Mastercard 3DS Gateway")
                .providerMessage("Đang chờ thông báo xác nhận giao dịch từ bên thứ 3...")
                .recipientPhone(momoPhone)
                .recipientName(momoName)
                .bankBin(bankBin)
                .bankName(bankName)
                .bankAccount(accountForVietQr)
                .vietQrImageUrl(vietQrImageUrl)
                .mobileCheckoutUrl(mobileCheckoutUrl)
                .qrImageForMobileCheckout(qrImageForMobileCheckout)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sessions.put(sessionId, session);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<PaymentSessionDTO> getSession(@PathVariable String sessionId) {
        PaymentSessionDTO session = sessions.get(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(session);
    }

    @PostMapping("/webhook/{sessionId}")
    public ResponseEntity<PaymentSessionDTO> receiveWebhook(
            @PathVariable String sessionId,
            @RequestBody(required = false) WebhookRequest req
    ) {
        PaymentSessionDTO session = sessions.get(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        String status = (req != null && req.getStatus() != null) ? req.getStatus().toUpperCase() : "SUCCESS";
        String txId = (req != null && req.getTransactionId() != null && !req.getTransactionId().isBlank())
                ? req.getTransactionId()
                : ("MOMO-" + (System.currentTimeMillis() % 100000000));

        session.setStatus(status);
        session.setTransactionId(txId);
        session.setUpdatedAt(LocalDateTime.now());

        if ("SUCCESS".equals(status)) {
            session.setPaidAt(LocalDateTime.now());
            session.setProviderMessage(
                    req != null && req.getMessage() != null
                            ? req.getMessage()
                            : "Webhook IPN: Đã nhận đủ " + String.format("%,.0f", session.getAmount()) + "₫ (Mã GD: " + txId + ")"
            );
        } else {
            session.setProviderMessage(
                    req != null && req.getMessage() != null
                            ? req.getMessage()
                            : "Giao dịch bị từ chối hoặc khách hàng đã hủy thanh toán."
            );
        }

        return ResponseEntity.ok(session);
    }

    @PostMapping("/sessions/{sessionId}/authorize-card")
    public ResponseEntity<PaymentSessionDTO> authorizeCard(
            @PathVariable String sessionId,
            @RequestBody CardAuthorizeRequest req
    ) {
        PaymentSessionDTO session = sessions.get(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        String rawCard = req.getCardNumber() != null ? req.getCardNumber().replaceAll("\\D", "") : "";
        String expiry = req.getExpiry() != null ? req.getExpiry().trim() : "";
        String cvv = req.getCvv() != null ? req.getCvv().replaceAll("\\D", "") : "";
        String holder = req.getCardHolder() != null ? req.getCardHolder().trim() : "";

        session.setUpdatedAt(LocalDateTime.now());

        if (rawCard.length() < 12 || rawCard.length() > 19) {
            session.setStatus("FAILED");
            session.setProviderMessage("Cổng thẻ từ chối: Số thẻ không hợp lệ (cần từ 12–19 chữ số).");
            return ResponseEntity.badRequest().body(session);
        }

        if (!isValidExpiry(expiry)) {
            session.setStatus("FAILED");
            session.setProviderMessage("Cổng thẻ từ chối: Thẻ đã hết hạn hoặc sai định dạng MM/YY.");
            return ResponseEntity.badRequest().body(session);
        }

        if (cvv.length() < 3 || cvv.length() > 4) {
            session.setStatus("FAILED");
            session.setProviderMessage("Cổng thẻ từ chối: Mã bảo mật CVV/CVC không hợp lệ.");
            return ResponseEntity.badRequest().body(session);
        }

        if (holder.length() < 2) {
            session.setStatus("FAILED");
            session.setProviderMessage("Cổng thẻ từ chối: Vui lòng nhập họ tên chủ thẻ.");
            return ResponseEntity.badRequest().body(session);
        }

        // Hỗ trợ kịch bản test thẻ bị từ chối nếu nhập CVV = 000
        if ("000".equals(cvv)) {
            session.setStatus("FAILED");
            session.setProviderMessage("Ngân hàng phát hành từ chối giao dịch (Mã lỗi: DO_NOT_HONOR / Insufficient Funds).");
            return ResponseEntity.badRequest().body(session);
        }

        String brand = detectCardBrand(rawCard);
        String last4 = rawCard.substring(rawCard.length() - 4);
        String txId = brand.toUpperCase() + "-3DS-" + (System.currentTimeMillis() % 100000000);

        session.setCardBrand(brand);
        session.setMaskedCardNumber("•••• •••• •••• " + last4);
        session.setTransactionId(txId);
        session.setStatus("SUCCESS");
        session.setPaidAt(LocalDateTime.now());
        session.setProviderMessage("Cổng " + brand + " 3D-Secure xác nhận trừ tiền thành công (Mã chuẩn chi: " + txId + ")");

        return ResponseEntity.ok(session);
    }

    @GetMapping(value = "/checkout/{sessionId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderMobileCheckoutPage(@PathVariable String sessionId) {
        PaymentSessionDTO s = sessions.get(sessionId);
        if (s == null) {
            return ResponseEntity.status(404).body("<html><body style='font-family:sans-serif;text-align:center;padding:40px;'><h2>Không tìm thấy phiên thanh toán</h2></body></html>");
        }

        String formattedAmount = String.format("%,.0f ₫", s.getAmount());
        boolean alreadyPaid = "SUCCESS".equals(s.getStatus());

        String html = "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'/>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0'/>"
                + "<title>Cổng Thanh Toán MoMo / VietQR - " + s.getStoreName() + "</title>"
                + "<style>"
                + "body{margin:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:linear-gradient(135deg,#a50064 0%,#d82d8b 100%);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:16px;box-sizing:border-box;color:#1e293b;}"
                + ".card{background:#fff;width:100%;max-width:400px;border-radius:24px;padding:24px;box-shadow:0 20px 50px rgba(0,0,0,0.3);text-align:center;}"
                + ".badge{display:inline-block;background:#fce7f3;color:#a50064;font-weight:700;font-size:12px;padding:6px 14px;border-radius:999px;margin-bottom:12px;}"
                + ".amount{font-size:32px;font-weight:900;color:#a50064;margin:8px 0 16px;}"
                + ".row{display:flex;justify-content:space-between;padding:10px 0;border-bottom:1px dashed #e2e8f0;font-size:14px;}"
                + ".label{color:#64748b;}"
                + ".val{font-weight:700;color:#0f172a;}"
                + ".btn{width:100%;border:none;border-radius:14px;padding:15px;font-size:16px;font-weight:700;cursor:pointer;margin-top:16px;transition:0.2s;}"
                + ".btn-pay{background:#a50064;color:#fff;box-shadow:0 8px 20px rgba(165,0,100,0.35);}"
                + ".btn-cancel{background:#f1f5f9;color:#475569;margin-top:10px;}"
                + ".ok-box{background:#ecfdf5;border:1px solid #a7f3d0;color:#047857;padding:16px;border-radius:16px;margin-top:16px;font-weight:600;}"
                + "</style></head><body>"
                + "<div class='card'>"
                + "<div class='badge'>CỔNG XÁC THỰC MOMO / VIETQR</div>"
                + "<h2 style='margin:0;font-size:20px;'>" + s.getStoreName() + "</h2>"
                + "<div class='amount'>" + formattedAmount + "</div>"
                + "<div class='row'><span class='label'>Người nhận</span><span class='val'>" + s.getRecipientName() + "</span></div>"
                + "<div class='row'><span class='label'>Số ĐT / TK</span><span class='val'>" + s.getRecipientPhone() + "</span></div>"
                + "<div class='row'><span class='label'>Mã tham chiếu</span><span class='val'>" + s.getReferenceCode() + "</span></div>"
                + "<div id='action-area'>"
                + (alreadyPaid
                ? "<div class='ok-box'>✅ Giao dịch đã được xác nhận thành công!<br/><small>Màn hình thu ngân POS đã tự động hoàn tất đơn hàng.</small></div>"
                : "<button class='btn btn-pay' onclick='sendWebhook(\"SUCCESS\")'>Xác nhận Thanh toán " + formattedAmount + "</button>"
                  + "<button class='btn btn-cancel' onclick='sendWebhook(\"FAILED\")'>Hủy giao dịch</button>")
                + "</div>"
                + "</div>"
                + "<script>"
                + "async function sendWebhook(status){"
                + "  const area = document.getElementById('action-area');"
                + "  area.innerHTML = '<p style=\"color:#a50064;font-weight:600;\">Đang gửi thông báo IPN Webhook về máy POS...</p>';"
                + "  try {"
                + "    const res = await fetch('/api/payment-gateway/webhook/" + sessionId + "', {"
                + "      method:'POST', headers:{'Content-Type':'application/json'},"
                + "      body: JSON.stringify({status: status, providerName: 'MoMo Mobile App'})"
                + "    });"
                + "    if(res.ok && status==='SUCCESS'){"
                + "      area.innerHTML = '<div class=\"ok-box\">✅ Thanh toán thành công!<br/><small>Đã gửi thông báo IPN về máy thu ngân POS.</small></div>';"
                + "    } else {"
                + "      area.innerHTML = '<div style=\"background:#fef2f2;color:#b91c1c;padding:14px;border-radius:12px;margin-top:14px;font-weight:600;\">Đã hủy giao dịch thanh toán.</div>';"
                + "    }"
                + "  } catch(e){ area.innerHTML = '<p style=\"color:red;\">Lỗi kết nối mạng!</p>'; }"
                + "}"
                + "</script></body></html>";

        return ResponseEntity.ok(html);
    }

    private boolean isValidExpiry(String expiry) {
        if (expiry == null || !expiry.matches("^(0[1-9]|1[0-2])/\\d{2}$")) {
            return false;
        }
        try {
            String[] parts = expiry.split("/");
            int month = Integer.parseInt(parts[0]);
            int year = 2000 + Integer.parseInt(parts[1]);
            YearMonth cardYm = YearMonth.of(year, month);
            return !cardYm.isBefore(YearMonth.now());
        } catch (Exception e) {
            return false;
        }
    }

    private String detectCardBrand(String number) {
        if (number.startsWith("4")) return "Visa";
        if (number.matches("^5[1-5].*") || number.matches("^2(2[2-9]|[3-6]\\d|7[01]|720).*")) return "Mastercard";
        if (number.startsWith("35")) return "JCB";
        if (number.startsWith("9704")) return "Napas";
        return "Visa/Debit";
    }

    private String resolveBankBin(String bankName) {
        if (bankName == null) return "970422";
        String lower = bankName.toLowerCase();
        if (lower.contains("vietcombank") || lower.contains("vcb")) return "970436";
        if (lower.contains("techcombank") || lower.contains("tcb")) return "970407";
        if (lower.contains("bidv")) return "970418";
        if (lower.contains("vietin") || lower.contains("ctg")) return "970415";
        if (lower.contains("acb")) return "970416";
        if (lower.contains("vpbank") || lower.contains("vpb")) return "970432";
        if (lower.contains("tpbank") || lower.contains("tpb")) return "970423";
        if (lower.contains("sacombank") || lower.contains("stb")) return "970403";
        if (lower.contains("agribank")) return "970405";
        if (lower.contains("hdbank")) return "970437";
        if (lower.contains("vib")) return "970441";
        if (lower.contains("shb")) return "970443";
        return "970422"; // Mặc định MB Bank (hỗ trợ chuẩn VietQR số điện thoại / số TK)
    }

    private String detectLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "localhost";
    }
}
