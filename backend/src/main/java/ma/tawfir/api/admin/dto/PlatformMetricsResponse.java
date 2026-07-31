package ma.tawfir.api.admin.dto;

public record PlatformMetricsResponse(
	int activeGroups,
	double defaultRatePercent,
	int openDisputes,
	int atRiskGroups
) {
}
