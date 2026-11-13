package com.dsikic.cluecose.core.tables

/**
 * Vendored Abbott white-box runtime tables (file names, 1:1 with LibreCRKit
 * CipherFn.swift `RuntimeTable`). The 58 `.bin` files ship verbatim in
 * `app/src/main/assets/RuntimeTables/` and in
 * `core/src/test/resources/RuntimeTables/`; only the interpreter logic is
 * ported. See `android/NOTICE.txt` for attribution (MIT, LibreCRKit).
 */
enum class RuntimeTable(val fileName: String) {
    SBOX19("sbox_19bit_lib_986819"),

    FIRST_PAIR_PROG_64E2B8("firstpair_prog_64e2b8_3041b4"),
    FIRST_PAIR_PROG_638840("firstpair_prog_638840_2f5046"),
    FIRST_PAIR_6388F0_LOW_SEED_STATICS("firstpair_6388f0_low_seed_statics_2f4d28"),
    FIRST_PAIR_6388F0_LOW_LOOP_STATICS("firstpair_6388f0_low_loop_statics_2fe600"),
    FIRST_PAIR_6388F0_SHARED_CONTEXT("firstpair_6388f0_shared_context_2cdae1"),
    FIRST_PAIR_6388F0_CALLER_LOOP_INTERLEAVED("firstpair_6388f0_caller_loop_interleaved_2cdfa9"),
    FIRST_PAIR_6388F0_LANE_TABLES("firstpair_6388f0_lane_tables_302678"),
    FIRST_PAIR_6388F0_SELECTOR_MUL("firstpair_6388f0_selector_mul_116968"),
    FIRST_PAIR_6388F0_SELECTOR_ADD("firstpair_6388f0_selector_add_119788"),
    FIRST_PAIR_63C278_U32_TABLES("firstpair_63c278_u32_tables_112588"),
    FIRST_PAIR_63C278_FOLD_TABLES("firstpair_63c278_fold_tables_2feb18"),
    FIRST_PAIR_633FA8_TAIL_FOLD_TABLES("firstpair_633fa8_tail_fold_tables_2fe798"),
    FIRST_PAIR_633FA8_TAIL_U32_LOW_TABLES("firstpair_633fa8_tail_u32_low_tables_112528"),
    FIRST_PAIR_633FA8_NULL_TABLES("firstpair_633fa8_null_tables_2fd1f1"),
    FIRST_PAIR_633FA8_NULL_NIBBLE("firstpair_633fa8_null_nibble_303a14"),
    FIRST_PAIR_PROCESS2_PUBLIC_TABLES("firstpair_process2_public_tables_3038c0"),
    FIRST_PAIR_PROG_67CC18("firstpair_prog_67cc18_369862"),
    FIRST_PAIR_FINAL_LEN_TABLES("firstpair_final_len_tables_372102"),
    FIRST_PAIR_DF80_ROUND_TABLES("firstpair_df80_round_tables_37120e"),
    FIRST_PAIR_FINALIZER_TABLES("firstpair_finalizer_tables_370e30"),
    FIRST_PAIR_679F48_SEED_TABLES("firstpair_679f48_seed_tables_37075e"),
    FIRST_PAIR_REDUCER_67EA28_NIBBLE("firstpair_reducer67ea28_nibble_373cf4"),
    FIRST_PAIR_PROG_67076C("firstpair_prog_67076c_35d3ef"),

    CHILD23_PROGRAM_REGION("child23_program_region_435cf0"),
    CHILD23_VM_START("child23_71fb38_vm_start_d843d0"),
    CHILD23_VM_DESC("child23_71fb38_vm_desc_d84770"),
    CHILD23_STATIC_TABLE("child23_static_table_71e870"),
    CHILD23_STATIC_COPY_CODE("child23_static_copy_code_71fca0"),
    CHILD23_TTABLE_B_EXT("child23_ttable_b_ext_976ea8_100000"),

    LIBAES_5DEFEC_ROUND1_TABLES("libaes_5defec_round1_tables_26f621"),
    LIBAES_ROUND1_TABLES("libaes_round1_tables_278dc2"),
    LIBAES_ROUND2_9_TABLES("libaes_round2_9_tables_279dc4"),
    LIBAES_KEYEXP_TABLES("libaes_keyexp_tables_275bbb"),
    LIBAES_KEYEXP_CONSTS("libaes_keyexp_consts_276bbc"),
    LIBAES_FINAL_KEY_TABLES("libaes_final_key_tables_276cfc"),
    LIBAES_FINAL_TABLE_INDEX("libaes_final_table_index_277cfc"),
    LIBAES_FINAL_TABLE_MAP("libaes_final_table_map_277d3c"),
    LIBAES_FINAL_TABLE_WORDS("libaes_final_table_words_270624"),

    CIPHER_FN_WBAES_T1("cipher_fn_wbaes_T1_lib200f18"),
    CIPHER_FN_WBAES_T2("cipher_fn_wbaes_T2_lib202633"),
    CIPHER_FN_WBAES_T3("cipher_fn_wbaes_T3_lib21220d"),
    CIPHER_FN_WBAES_T4("cipher_fn_wbaes_T4_lib21a1c1"),
    CIPHER_FN_WBAES_T5("cipher_fn_wbaes_T5_lib221c92"),

    BIT_MASK_TABLE("bit_mask_table_lib_1267e0"),
    BYTECODE_LIB("bytecode_lib_b25d20"),
    DECODE_TABLE("decode_table_lib_237dcc"),
    GF_REDUCE_TABLE("gf_reduce_table_lib_23840c"),
    PARAMS("params_lib_22a1a0"),
    T5_SEED("t5_seed_lib_b25708"),
    SBOX_12BIT_FULL("sbox_12bit_full"),
    SBOX_12BIT_LIB("sbox_12bit_lib_9a681a"),
    SINGLETON_4K("singleton_4k"),
    PHASE2_PAIRS("phase2_pairs"),
    PHASE5_KEYSCHED_REGION("phase5_keysched_region_274000"),
    PHONE_CERT_162B("phone_cert_162b"),
    PHONE_CERT_FIRSTPAIR("phone_cert_firstpair"),
    ;

    fun load(source: TablesSource): ByteArray =
        source.load(this)
}
