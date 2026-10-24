package it.glknet.financial_manager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Toglie il vecchio vincolo unico (user_id, keyword) di category_rules, che impediva una regola per le uscite e una
 * per le entrate con la stessa parola chiave. ddl-auto=update non cancella i vincoli, quindi lo facciamo all'avvio;
 * se non c'e' (database nuovo o gia' sistemato) non fa nulla.
 */
@Component
@RequiredArgsConstructor
public class CategoryRulesConstraintFix implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CategoryRulesConstraintFix.class);

	private final JdbcTemplate jdbc;

	@Override
	public void run(ApplicationArguments args) {
		for (String name : jdbc.queryForList("""
				SELECT con.conname FROM pg_constraint con JOIN pg_class rel ON rel.oid = con.conrelid
				WHERE rel.relname = 'category_rules' AND con.contype = 'u'""", String.class)) {
			jdbc.execute("ALTER TABLE category_rules DROP CONSTRAINT \"" + name.replace("\"", "") + "\"");
			log.info("Tolto il vincolo unico {} da category_rules", name);
		}
	}
}
