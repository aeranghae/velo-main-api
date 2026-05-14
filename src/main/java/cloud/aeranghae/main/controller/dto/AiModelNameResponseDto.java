package cloud.aeranghae.main.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AiModelNameResponseDto {
    private String modelName;
    private String provider;
}