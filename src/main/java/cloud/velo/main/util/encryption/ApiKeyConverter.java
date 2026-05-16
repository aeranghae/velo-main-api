package cloud.velo.main.util.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;

@Converter
@RequiredArgsConstructor
public class ApiKeyConverter implements AttributeConverter<String, String> {

    private final EncryptionUtil encryptionUtil;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        // 엔티티 필드(평문) -> DB 컬럼(암호문)
        return (attribute == null) ? null : encryptionUtil.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        // DB 컬럼(암호문) -> 엔티티 필드(평문)
        return (dbData == null) ? null : encryptionUtil.decrypt(dbData);
    }
}