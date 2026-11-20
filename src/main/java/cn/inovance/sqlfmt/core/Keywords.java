package cn.inovance.sqlfmt.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * R1 / R2：Oracle 关键字白名单。
 * 只有白名单内的词才大写，用户标识符（表名/列名/变量名/过程名）保持原样。
 * 与 plsql_formatter.py 的 KEYWORDS / REVIEW_WORDS 保持一致。
 */
public final class Keywords {

    private static final String WORDS =
        "SELECT FROM WHERE INSERT UPDATE DELETE MERGE INTO VALUES SET COMMIT ROLLBACK SAVEPOINT " +
        "CREATE OR REPLACE PACKAGE BODY PROCEDURE FUNCTION IS AS RETURN DECLARE BEGIN END EXCEPTION " +
        "IF THEN ELSIF ELSE LOOP WHILE FOR IN OUT NOCOPY EXIT CONTINUE GOTO RAISE " +
        "CASE WHEN OTHERS NULL TRUE FALSE AND OR NOT BETWEEN LIKE EXISTS DISTINCT ALL ANY " +
        "GROUP BY ORDER HAVING CONNECT START WITH PRIOR UNION MINUS INTERSECT " +
        "JOIN INNER LEFT RIGHT FULL OUTER CROSS NATURAL ON USING " +
        "ASC DESC NULLS FIRST LAST " +
        "ROWNUM ROWID LEVEL DUAL SYSDATE SYSTIMESTAMP CURRENT_DATE USER UID " +
        "NVL NVL2 DECODE TO_CHAR TO_DATE TO_NUMBER TO_TIMESTAMP TO_CLOB TO_BLOB TO_RAW " +
        "SUBSTR INSTR LENGTH UPPER LOWER INITCAP TRIM LTRIM RTRIM LPAD RPAD REPLACE TRANSLATE " +
        "ROUND TRUNC CEIL FLOOR ABS MOD POWER SQRT SIGN COUNT SUM AVG MAX MIN STDDEV VARIANCE " +
        "GREATEST LEAST COALESCE NULLIF ADD_MONTHS LAST_DAY MONTHS_BETWEEN NEXT_DAY " +
        "CAST EXTRACT DUMP VSIZE BITAND " +
        "PRAGMA AUTONOMOUS_TRANSACTION SERIALLY_REUSABLE EXCEPTION_INIT " +
        "EXECUTE IMMEDIATE BULK COLLECT LIMIT RETURNING " +
        "FORALL INDICES PAIRS SAVE " +
        "OVER PARTITION RANGE ROWS UNBOUNDED PRECEDING FOLLOWING CURRENT ROW KEEP DENSE_RANK " +
        "RANK ROW_NUMBER LAG LEAD LISTAGG FIRST_VALUE LAST_VALUE NTILE " +
        "VARCHAR2 VARCHAR NVARCHAR2 NCHAR CHAR CHARACTER NUMBER NUMERIC DECIMAL DEC INT INTEGER " +
        "PLS_INTEGER BINARY_INTEGER SIMPLE_INTEGER BOOLEAN DATE TIMESTAMP CLOB BLOB BFILE " +
        "RAW LONG FLOAT DOUBLE PRECISION SMALLINT UROWID " +
        "TYPE RECORD TABLE VARRAY REF CURSOR STRUCT SUBTYPE " +
        "CONSTANT DEFAULT NOCOPY READ ONLY WRITE " +
        "AUTHID CURRENT_USER DEFINER SQLCODE SQLERRM SQL " +
        "INTERVAL";

    /** 需人工评估是否大写的词：不在白名单内，默认不动（R2，--report 时列出）。 */
    private static final String REVIEW = "desc asc log name key value mode comment title label";

    public static final Set<String> UPPER = immutable(WORDS);
    public static final Set<String> REVIEW_WORDS = immutable(REVIEW);

    private Keywords() {}

    private static Set<String> immutable(String spaceSeparated) {
        Set<String> set = new HashSet<>();
        for (String w : spaceSeparated.split("\\s+")) {
            if (!w.isEmpty()) set.add(w.toUpperCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(set);
    }

    public static boolean isKeyword(String word) {
        return UPPER.contains(word.toUpperCase(Locale.ROOT));
    }

    public static boolean isReviewWord(String word) {
        return REVIEW_WORDS.contains(word.toLowerCase(Locale.ROOT));
    }
}
