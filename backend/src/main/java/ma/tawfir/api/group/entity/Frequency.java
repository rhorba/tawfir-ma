package ma.tawfir.api.group.entity;

import java.time.LocalDate;

public enum Frequency {
	WEEKLY {
		@Override
		public LocalDate advance(LocalDate date, int cycles) {
			return date.plusWeeks(cycles);
		}
	},
	MONTHLY {
		@Override
		public LocalDate advance(LocalDate date, int cycles) {
			return date.plusMonths(cycles);
		}
	};

	public abstract LocalDate advance(LocalDate date, int cycles);

}
