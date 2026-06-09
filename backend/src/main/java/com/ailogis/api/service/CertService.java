package com.ailogis.api.service;

import com.ailogis.api.dto.CertDTO;
import com.ailogis.api.entity.CertificationType;
import com.ailogis.api.repository.CertificationSubmitRepository;
import com.ailogis.api.repository.CertificationTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CertService {

    private final CertificationTypeRepository certRepository;
    private final CertificationSubmitRepository certificationSubmitRepository;
    private final FileStorageService fileStorageService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<CertDTO> getCerts(String label, String labelDesc) {
        List<CertificationType> certs = certRepository.findAll();

        // Lọc theo label (Nếu có truyền)
        if (label != null && !label.isEmpty()) {
            certs = certs.stream()
                    .filter(c -> c.getLabel() != null && c.getLabel().toLowerCase().contains(label.toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Lọc theo labelDesc (Tương ứng với lawReferences)
        if (labelDesc != null && !labelDesc.isEmpty()) {
            certs = certs.stream()
                    .filter(c -> c.getLawReferences() != null && c.getLawReferences().toLowerCase().contains(labelDesc.toLowerCase()))
                    .collect(Collectors.toList());
        }

        return certs.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Transactional
    public CertDTO createCert(CertDTO dto) {
        CertificationType cert = CertificationType.builder()
                .label(dto.label())
                .lawReferences(dto.labelDesc())
                .updateDate(dto.update() != null ? LocalDate.parse(dto.update(), formatter) : LocalDate.now())
                .build();

        return mapToDTO(certRepository.save(cert));
    }

    @Transactional
    public CertDTO updateCert(Long certID, CertDTO dto) {
        CertificationType cert = certRepository.findById(certID)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chứng chỉ này!"));

        if (dto.label() != null) cert.setLabel(dto.label());
        if (dto.labelDesc() != null) cert.setLawReferences(dto.labelDesc());
        if (dto.update() != null) cert.setUpdateDate(LocalDate.parse(dto.update(), formatter));

        return mapToDTO(certRepository.save(cert));
    }

    @Transactional
    public String uploadPdf(Long certID, MultipartFile file) {
        CertificationType cert = certRepository.findById(certID)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chứng chỉ này!"));

        // 1. Nếu chứng chỉ này đã có PDF cũ -> Xóa khỏi S3 để tiết kiệm dung lượng
        if (cert.getPdfLink() != null && !cert.getPdfLink().isEmpty()) {
            fileStorageService.deleteFile(cert.getPdfLink());
        }

        // 2. Upload PDF mới lên S3
        String newPdfUrl = fileStorageService.storeFile(file, "certs");

        // 3. Cập nhật link mới vào Database
        cert.setPdfLink(newPdfUrl);
        certRepository.save(cert);

        return newPdfUrl;
    }

    @Transactional
    public void deleteCertType(Long certID) {
        // Kiểm tra xem có kho nào đang dùng loại chứng chỉ này không
        boolean isUsed = certificationSubmitRepository.existsByTypeId(certID);
        if (isUsed) {
            throw new RuntimeException("Không thể xóa: Đang có kho sử dụng loại chứng chỉ này!");
        }
        certRepository.deleteById(certID);
    }

    // Hàm Helper chuyển Entity thành DTO
    private CertDTO mapToDTO(CertificationType c) {
        return new CertDTO(
                c.getId().toString(),
                c.getLabel(),
                c.getLawReferences(),
                c.getUpdateDate() != null ? c.getUpdateDate().format(formatter) : null,
                c.getPdfLink()
        );
    }
}