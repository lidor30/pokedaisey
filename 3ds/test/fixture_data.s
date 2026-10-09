@ The fixture's RAM dumps, linked into the test ROM (see fixture_rom.c).
@ FIXTURE_EWRAM / FIXTURE_IWRAM are the dump paths, passed with -D (assembled
@ with -x assembler-with-cpp; see build_fixture_roms.sh).
    .section .rodata
    .balign 4
    .global fixture_ewram, fixture_ewram_end, fixture_iwram, fixture_iwram_end
fixture_ewram:
    .incbin FIXTURE_EWRAM
fixture_ewram_end:
    .balign 4
fixture_iwram:
    .incbin FIXTURE_IWRAM
fixture_iwram_end:
