#include "stdlib.h"
#include "stdio.h"
#include "stdbool.h"
#include "string.h"

// ltconst.c /////////////////////////////////////////////////////////////////
typedef short WORD;
typedef unsigned short ADDRESS;
typedef ADDRESS WORDU;
typedef int DWORD;
typedef unsigned int DWORDU;

#ifdef TEST
  const bool TESTING = true;
#else
  const bool TESTING = false;
#endif
const char* TEST_FILE = "test.vm";

#ifdef DEBUG
  const bool DEBUGGING = true;
#else
  const bool DEBUGGING = false;
#endif

// Sizes for the various memorys
const ADDRESS END_MEMORY = 0xffff;
const ADDRESS END_RETURN = 0x1000;
const ADDRESS END_STACK = 0x1000;

const WORDU WORD_SIZE = 16;
const WORDU BYTE_SIZE = 8;
const WORDU BUFFER_SIZE = 0x0400;

const WORDU DEFAULT_SCALE = 3;
const WORDU SCALE_MAX = 10;
const DWORDU SCALES[ 10 ] = {
  1, 10, 100, 1000, 10000, 100000,
  1000000, 10000000, 100000000,
  1000000000
};

// Exit codes //
const size_t EXIT_MEM = 1;
const size_t EXIT_LEN = 2;
const size_t EXIT_FILE = 3;
const size_t EXIT_SOF = 4;
const size_t EXIT_SUF = 5;
const size_t EXIT_POB = 6;
const size_t EXIT_OP = 7;
const size_t EXIT_STR = 8;
const size_t EXIT_ARGS = 9;
const size_t EXIT_RSOF = 10;
const size_t EXIT_RSUF = 11;

// ltrun.h ///////////////////////////////////////////////////////////////////
typedef enum op_codes { HALT=0,
  PUSH, POP, LOAD, STORE,  // 04
  FST, SEC, NTH,  // 07
  SWAP, ROT,  // 09
  RPUSH, RPOP, RGRAB,  // 0C

  DPUSH, DPOP, DLOAD, DSTORE,  // 10
  DFST, DSEC, DNTH,  // 13
  DSWAP, DROT,  // 15
  DRPUSH, DRPOP, DRGRAB,  // 18

  ADD, SUB, MULT, DIV, MOD,  // 1D
  EQ, LT, GT,  // 20
  MULTU, DIVU, MODU, LTU, GTU,  // 25

  SL, SR,  // 27
  AND, OR, NOT,  // 2A

  DADD, DSUB, DMULT, DDIV, DMOD,  // 2F
  DEQ, DLT, DGT,  // 32
  DMULTU_unused, DDIVU, DMODU, DLTU, DGTU,  // 37

  DSL, DSR,  // 39
  DAND, DOR, DNOT,  // 3C

  JUMP, BRANCH, CALL, RET,  // 40
  DSP, PC, BFP, FMP,  // 44

  WPRN, DPRN, WPRNU, DPRNU, FPRN, FPRNSC,  // 4A
  PRNCH, PRN, PRNLN, PRNSP_unused, PRNMEM,  // 4F

  WREAD, DREAD, FREAD, FREADSC,  // 53
  READCH, READ_unused, READLN, READSP_unused,  // 57

  BFSTORE, BFLOAD,  // 59
  HIGH, LOW, UNPACK, PACK,  // 5D

  MEMCOPY, STRCOPY,  // 5F
  FMULT, FDIV, FMULTSC, FDIVSC,  // 63

  PRNPK, READCH_BF, STREQ, MEMEQ, // 67
  IS_EOF, RESET_EOF, // 69

  BRKPNT, // 6A
} OP_CODE;

enum copy_codes { MEM_BUF = 0, BUF_MEM };

/// ltmem.h //////////////////////////////////////////////////////////////////
static inline DWORD get_dword(WORD* mem, ADDRESS pos) {
  return (mem[pos] << WORD_SIZE) | (mem[pos+1] & 0xffff);
}

static inline void set_dword(WORD* mem, ADDRESS pos, DWORD val) {
  mem[pos] = val >> WORD_SIZE;
  mem[pos+1] = (WORD)val;
}

static inline DWORD get_rev_dword(WORD* mem, ADDRESS pos) {
  return (mem[pos+1] << WORD_SIZE) | (mem[pos] & 0xffff);
}

static inline void set_rev_dword(WORD* mem, ADDRESS pos, DWORD val) {
  mem[pos+1] = val >> WORD_SIZE;
  mem[pos] = (WORD)val;
}

static inline WORDU string_length(WORD* mem, ADDRESS start) {
  ADDRESS atemp = start;
  while (atemp) {
    if (!mem[atemp]) break;
    atemp--;
  }
  return start - (atemp - 1);
}

inline static WORDU mem_equal(WORD* mem, ADDRESS first,
                              ADDRESS second, ADDRESS length) {
  for (ADDRESS i = 0; i < length; i++)
    if (mem[first + i] != mem[second + i])
      return 0;
  return 1;
}

/// ltio.c ///////////////////////////////////////////////////////////////////
void display_range(WORD* mem, ADDRESS start, ADDRESS end, bool debug) {
  if (debug && end - 8 > start) {
    start = end - 8; 
    fprintf(stderr, "... ");
  }

  for (ADDRESS i = start; i < end; i++) {
    if (debug)
      fprintf(stderr, "%hx(%hd) ", mem[i], mem[i]);
    else
      printf("%04hx ", mem[i]);
  }

  if (debug)
    fprintf(stderr, "->\n");
  else
    printf("\n");
}

void print_string(WORD* mem, ADDRESS start, ADDRESS max) {
  ADDRESS atemp = start;
  while (atemp < max) {
    WORD chars = mem[atemp];
    WORD low = chars & 0xff;
    WORD high = chars >> BYTE_SIZE;

    if (!low) break;
    printf("%c", low);

    if (!high) break;
    printf("%c", high);

    atemp++;
  }
}

WORD read_string(WORD* mem, ADDRESS start, ADDRESS max) {
  ADDRESS atemp = start;
  bool first = true;
  WORDU two_chars = 0;

  while (atemp < max - 1) {
    char ch;
    scanf("%c", &ch);

    if (ch == '\n' || ch == EOF) {
      if (first) {
        two_chars = 0;
      } else {
        two_chars &= 0xff;
      }
      mem[atemp] = two_chars;
      mem[atemp + 1] = 0;
      return ch == EOF ? -1 : 1;

    } else {
      if (first) {
        first = false;
        two_chars = ch & 0xff;
      } else {
        first = true;
        two_chars |= (ch << BYTE_SIZE);
        mem[atemp++] = two_chars;
      }
    }
  }
  mem[atemp + 1] = 0;
  return 0;
}

void display_op_name(OP_CODE op, FILE* stream) {
  switch (op) {
    case HALT: fprintf(stream, "HALT"); break;
    case PUSH: fprintf(stream, "PUSH"); break;
    case POP: fprintf(stream, "POP"); break;
    case LOAD: fprintf(stream, "LOAD"); break;
    case STORE: fprintf(stream, "STORE"); break;
    case FST: fprintf(stream, "FST"); break;
    case SEC: fprintf(stream, "SEC"); break;
    case NTH: fprintf(stream, "NTH"); break;
    case SWAP: fprintf(stream, "SWAP"); break;
    case ROT: fprintf(stream, "ROT"); break;
    case RPUSH: fprintf(stream, "RPUSH"); break;
    case RPOP: fprintf(stream, "RPOP"); break;
    case RGRAB: fprintf(stream, "RGRAB"); break;
    case DPUSH: fprintf(stream, "DPUSH"); break;
    case DPOP: fprintf(stream, "DPOP"); break;
    case DLOAD: fprintf(stream, "DLOAD"); break;
    case DSTORE: fprintf(stream, "DSTORE"); break;
    case DFST: fprintf(stream, "DFST"); break;
    case DSEC: fprintf(stream, "DSEC"); break;
    case DNTH: fprintf(stream, "DNTH"); break;
    case DSWAP: fprintf(stream, "DSWAP"); break;
    case DROT: fprintf(stream, "DROT"); break;
    case DRPUSH: fprintf(stream, "DRPUSH"); break;
    case DRPOP: fprintf(stream, "DRPOP"); break;
    case DRGRAB: fprintf(stream, "DRGRAB"); break;
    case ADD: fprintf(stream, "ADD"); break;
    case SUB: fprintf(stream, "SUB"); break;
    case MULT: fprintf(stream, "MULT"); break;
    case DIV: fprintf(stream, "DIV"); break;
    case MOD: fprintf(stream, "MOD"); break;
    case EQ: fprintf(stream, "EQ"); break;
    case LT: fprintf(stream, "LT"); break;
    case GT: fprintf(stream, "GT"); break;
    case MULTU: fprintf(stream, "MULTU"); break;
    case DIVU: fprintf(stream, "DIVU"); break;
    case MODU: fprintf(stream, "MODU"); break;
    case LTU: fprintf(stream, "LTU"); break;
    case GTU: fprintf(stream, "GTU"); break;
    case SL: fprintf(stream, "SL"); break;
    case SR: fprintf(stream, "SR"); break;
    case AND: fprintf(stream, "AND"); break;
    case OR: fprintf(stream, "OR"); break;
    case NOT: fprintf(stream, "NOT"); break;
    case DADD: fprintf(stream, "DADD"); break;
    case DSUB: fprintf(stream, "DSUB"); break;
    case DMULT: fprintf(stream, "DMULT"); break;
    case DDIV: fprintf(stream, "DDIV"); break;
    case DMOD: fprintf(stream, "DMOD"); break;
    case DEQ: fprintf(stream, "DEQ"); break;
    case DLT: fprintf(stream, "DLT"); break;
    case DGT: fprintf(stream, "DGT"); break;
    case DMULTU_unused: fprintf(stream, "DMULTU_unused"); break;
    case DDIVU: fprintf(stream, "DDIVU"); break;
    case DMODU: fprintf(stream, "DMODU"); break;
    case DLTU: fprintf(stream, "DLTU"); break;
    case DGTU: fprintf(stream, "DGTU"); break;
    case DSL: fprintf(stream, "DSL"); break;
    case DSR: fprintf(stream, "DSR"); break;
    case DAND: fprintf(stream, "DAND"); break;
    case DOR: fprintf(stream, "DOR"); break;
    case DNOT: fprintf(stream, "DNOT"); break;
    case JUMP: fprintf(stream, "JUMP"); break;
    case BRANCH: fprintf(stream, "BRANCH"); break;
    case CALL: fprintf(stream, "CALL"); break;
    case RET: fprintf(stream, "RET"); break;
    case DSP: fprintf(stream, "DSP"); break;
    case PC: fprintf(stream, "PC"); break;
    case BFP: fprintf(stream, "BFP"); break;
    case FMP: fprintf(stream, "FMP"); break;
    case WPRN: fprintf(stream, "WPRN"); break;
    case DPRN: fprintf(stream, "DPRN"); break;
    case WPRNU: fprintf(stream, "WPRNU"); break;
    case DPRNU: fprintf(stream, "DPRNU"); break;
    case FPRN: fprintf(stream, "FPRN"); break;
    case FPRNSC: fprintf(stream, "FPRNSC"); break;
    case PRNCH: fprintf(stream, "PRNCH"); break;
    case PRN: fprintf(stream, "PRN"); break;
    case PRNLN: fprintf(stream, "PRNLN"); break;
    case PRNSP_unused: fprintf(stream, "PRNSP_unused"); break;
    case PRNMEM: fprintf(stream, "PRNMEM"); break;
    case WREAD: fprintf(stream, "WREAD"); break;
    case DREAD: fprintf(stream, "DREAD"); break;
    case FREAD: fprintf(stream, "FREAD"); break;
    case FREADSC: fprintf(stream, "FREADSC"); break;
    case READCH: fprintf(stream, "READCH"); break;
    case READ_unused: fprintf(stream, "READ_unused"); break;
    case READLN: fprintf(stream, "READLN"); break;
    case READSP_unused: fprintf(stream, "READSP_unused"); break;
    case HIGH: fprintf(stream, "HIGH"); break;
    case LOW: fprintf(stream, "LOW"); break;
    case BFSTORE: fprintf(stream, "BFSTORE"); break;
    case BFLOAD: fprintf(stream, "BFLOAD"); break;
    case UNPACK: fprintf(stream, "UNPACK"); break;
    case PACK: fprintf(stream, "PACK"); break;
    case MEMCOPY: fprintf(stream, "MEMCOPY"); break;
    case STRCOPY: fprintf(stream, "STRCOPY"); break;
    case FMULT: fprintf(stream, "FMULT"); break;
    case FDIV: fprintf(stream, "FDIV"); break;
    case FMULTSC: fprintf(stream, "FMULTSC"); break;
    case FDIVSC: fprintf(stream, "FDIVSC"); break;
    case PRNPK: fprintf(stream, "PRNPK"); break;
    default: fprintf(stream, "code=%hx (%hd)", op, op); break;
  }
}

void debug_info_display(WORD* data_stack, WORD* return_stack, ADDRESS dsp,
                        ADDRESS rsp, ADDRESS pc, WORD op) {
  // print stacks and pointers
  fflush(stdout);
  fprintf(stderr, "\nDstack: ");
  display_range(data_stack, 0x0001, dsp + 1, DEBUGGING);
  fprintf(stderr, "Rstack: ");
  display_range(return_stack, 0x0001, rsp + 1, DEBUGGING);
  fprintf(stderr, "PC: %hx (%hu), Next OP: ", pc, pc);
  display_op_name(op, stderr);
  fprintf(stderr, "\n");
}

bool debug_step() {
  char *buffer = NULL;
  size_t size;

  fflush(stdout);
  fprintf(stderr, "\n***Step? ");
  ssize_t res = getline(&buffer, &size, stdin);
  return res <= 1;
}

/// ltrun.c //////////////////////////////////////////////////////////////////
size_t execute(WORD* memory, size_t length, WORD* data_stack, WORD* return_stack) {
  // Declare and initialize memory pointer "registers"
  ADDRESS dsp, rsp, pc, bfp, fmp;
  dsp = 0;
  rsp = 0;
  pc = 0;
  bfp = length;
  fmp = length + BUFFER_SIZE;

  // conditions
  bool eof = false;
  bool wait_for_break = false;

  // Declare some temporary "registers" for working with intermediate values
  ADDRESS atemp;
  WORD temp;
  WORDU utemp;
  DWORD dtemp;
  
  // Run the program in memory
  bool run = true;
  while (run) {
    // Print stack, op code, and pc before every execution
    if (DEBUGGING && !wait_for_break) {
      debug_info_display(data_stack, return_stack, dsp, rsp, pc, memory[pc] & 0xff);
      bool step = debug_step();
      if (!step) {
        wait_for_break = true;
        fprintf(stderr, "\n* Skipping To Break Point *\n\n");
      }
    }

    // Catch some common pointer/address errors
    if (pc >= bfp) {
      fprintf(stderr,
              "Error: program counter out of bounds, pc: %hx, bfp: %hx\n",
              pc, bfp);
      return EXIT_POB;
    } else if (dsp > 0x8000) {  // i.e. it has wrapped around into negatives
      fprintf(stderr, "Error: stack underflow, sp: %hx (%hd)\n", dsp, dsp);
      return EXIT_SUF;
    } else if (dsp > END_STACK) {
      fprintf(stderr, "Error: stack overflow, sp: %hx (%hd)\n", dsp, dsp);
      return EXIT_SOF;
    } else if (rsp > 0x8000) {  // i.e. it has wrapped around into negatives
      fprintf(stderr, "Error: return stack underflow, sp: %hx (%hd)\n",
              rsp, rsp);
      return EXIT_RSUF;
    } else if (rsp > END_RETURN) {
      fprintf(stderr, "Error: return stack overflow, sp: %hx (%hd)\n",
              rsp, rsp);
      return EXIT_RSOF;
    }

    // Switch to cover each opcode. It is too long, but for simplicity and
    // efficiency it is kept this way, with larger operations calling
    // functions. The functions for things like unpacking and packing
    // double words are declared as inline so they will be more efficient.
    // Larger functions for things like io operations are regular functions
    // because they are not really hurt by the function call.
    switch (memory[pc] & 0xff) {
      case HALT:
        run = false;
        break;
      case BRKPNT:
        wait_for_break = false;
        break;

      /// Stack Manipulation ///
      case PUSH:
        data_stack[++dsp] = memory[++pc];
        break;
      case POP:
        dsp--;
        break;
      case LOAD:
        if (memory[pc] >> BYTE_SIZE & 1)
          data_stack[dsp] = memory[(ADDRESS)data_stack[dsp]];
        else
          data_stack[dsp] = memory[fmp + (ADDRESS)data_stack[dsp]];
        break;
      case STORE:
        if (memory[pc] >> BYTE_SIZE & 1) {
          memory[(ADDRESS)data_stack[dsp]] = data_stack[dsp-1];
        } else {
          memory[fmp + (ADDRESS)data_stack[dsp]] = data_stack[dsp-1];
        }
        dsp-=2;
        break;
      case FST:
        data_stack[dsp+1] = data_stack[dsp];
        dsp++;
        break;
      case SEC:
        data_stack[dsp+1] = data_stack[dsp-1];
        dsp++;
        break;
      case NTH:
        data_stack[dsp] = data_stack[dsp - data_stack[dsp] - 1];
        break;
      case SWAP:
        temp = data_stack[dsp];
        data_stack[dsp] = data_stack[dsp-1];
        data_stack[dsp-1] = temp;
        break;
      case ROT:
        temp = data_stack[dsp-2];
        data_stack[dsp-2] = data_stack[dsp-1];
        data_stack[dsp-1] = data_stack[dsp];
        data_stack[dsp] = temp;
        break;
      case RPUSH:
        return_stack[++rsp] = data_stack[dsp--];
        break;
      case RPOP:
        data_stack[++dsp] = return_stack[rsp--];
        break;
      case RGRAB:
        data_stack[++dsp] = return_stack[rsp];
        break;

      /// Double Word Stack Manipulation ///
      case DPUSH:
        data_stack[++dsp] = memory[++pc];
        data_stack[++dsp] = memory[++pc];
        break;
      case DPOP:
        dsp-=2;
        break;
      case DLOAD:
        atemp = data_stack[dsp--];
        if (memory[pc] >> BYTE_SIZE & 1) {
          data_stack[++dsp] = memory[atemp];
          data_stack[++dsp] = memory[atemp + 1];
        } else {
          data_stack[++dsp] = memory[fmp + atemp];
          data_stack[++dsp] = memory[fmp + atemp + 1];
        }
        break;
      case DSTORE:
        atemp = data_stack[dsp--];
        if (memory[pc] >> BYTE_SIZE & 1) {
          memory[atemp] = data_stack[dsp-1];
          memory[atemp + 1] = data_stack[dsp];
        } else {
          memory[fmp + atemp] = data_stack[dsp-1];
          memory[fmp + atemp + 1] = data_stack[dsp];
        }
        dsp-=2;
        break;
      case DFST:
        data_stack[dsp+1] = data_stack[dsp-1];
        data_stack[dsp+2] = data_stack[dsp];
        dsp+=2;
        break;
      case DSEC:
        data_stack[dsp+1] = data_stack[dsp-3];
        data_stack[dsp+2] = data_stack[dsp-2];
        dsp+=2;
        break;
      case DNTH:
        atemp = data_stack[dsp--] * 2;
        data_stack[dsp+1] = data_stack[dsp - atemp - 1];
        data_stack[dsp+2] = data_stack[dsp - atemp];
        dsp+=2;
        break;
      case DSWAP:
        temp = data_stack[dsp];
        data_stack[dsp] = data_stack[dsp-2];
        data_stack[dsp-2] = temp;

        temp = data_stack[dsp-1];
        data_stack[dsp-1] = data_stack[dsp-3];
        data_stack[dsp-3] = temp;
        break;
      case DROT:
        temp = data_stack[dsp-5];
        data_stack[dsp-5] = data_stack[dsp-3];
        data_stack[dsp-3] = data_stack[dsp-1];
        data_stack[dsp-1] = temp;

        temp = data_stack[dsp-4];
        data_stack[dsp-4] = data_stack[dsp-2];
        data_stack[dsp-2] = data_stack[dsp];
        data_stack[dsp] = temp;
        break;
      case DRPUSH:
        return_stack[++rsp] = data_stack[dsp-1];
        return_stack[++rsp] = data_stack[dsp];
        dsp-=2;
        break;
      case DRPOP:
        data_stack[++dsp] = return_stack[rsp-1];
        data_stack[++dsp] = return_stack[rsp];
        rsp-=2;
        break;
      case DRGRAB:
        data_stack[++dsp] = return_stack[rsp-1];
        data_stack[++dsp] = return_stack[rsp];
        break;

      /// Word Arithmetic ///
      case ADD:
        data_stack[dsp-1] = data_stack[dsp-1] + data_stack[dsp];
        dsp--;
        break;
      case SUB:
        data_stack[dsp-1] = data_stack[dsp-1] - data_stack[dsp];
        dsp--;
        break;
      case MULT:
        data_stack[dsp-1] = data_stack[dsp-1] * data_stack[dsp];
        dsp--;
        break;
      case DIV:
        data_stack[dsp-1] = data_stack[dsp-1] / data_stack[dsp];
        dsp--;
        break;
      case MOD:
        data_stack[dsp-1] = data_stack[dsp-1] % data_stack[dsp];
        dsp--;
        break;

      /// Signed Comparisson ///
      case EQ:
        data_stack[dsp-1] = data_stack[dsp-1] == data_stack[dsp];
        dsp--;
        break;
      case LT:
        data_stack[dsp-1] = data_stack[dsp-1] < data_stack[dsp];
        dsp--;
        break;
      case GT:
        data_stack[dsp-1] = data_stack[dsp-1] > data_stack[dsp];
        dsp--;
        break;

      /// Unsigned Artihmetic and Comparisson ///
      case MULTU:
        {
          // large signed numbers cast to unsigned dword as 0xffff____
          // so we have to zero those bits before the calculation 
          DWORDU a = (DWORDU)data_stack[dsp-1] & 0xffff;
          DWORDU b = (DWORDU)data_stack[dsp] & 0xffff;
          DWORDU res = a * b;
          data_stack[dsp-1] = res >> 16; 
          data_stack[dsp] = res;
        }
        break;
      case DIVU:
        data_stack[dsp-1] = (WORDU)data_stack[dsp-1] / (WORDU)data_stack[dsp];
        dsp--;
        break;
      case MODU:
        data_stack[dsp-1] = (WORDU)data_stack[dsp-1] % (WORDU)data_stack[dsp];
        dsp--;
        break;
      case LTU:
        data_stack[dsp-1] = (WORDU)data_stack[dsp-1] < (WORDU)data_stack[dsp];
        dsp--;
        break;
      case GTU:
        data_stack[dsp-1] = (WORDU)data_stack[dsp-1] > (WORDU)data_stack[dsp];
        dsp--;
        break;

      /// Double Arithmetic and Comparisson ///
      case DADD:
        set_dword(data_stack, dsp-3, get_dword(data_stack, dsp-3)
                                     + get_dword(data_stack, dsp-1));
        dsp-=2;
        break;
      case DSUB:
        set_dword(data_stack, dsp-3, get_dword(data_stack, dsp-3)
                                     - get_dword(data_stack, dsp-1));
        dsp-=2;
        break;
      case DMULT:
        set_dword(data_stack, dsp-3, get_dword(data_stack, dsp-3)
                                     * get_dword(data_stack, dsp-1));
        dsp-=2;
        break;
      case DDIV:
        set_dword(data_stack, dsp-3, get_dword(data_stack, dsp-3)
                                     / get_dword(data_stack, dsp-1));
        dsp-=2;
        break;
      case DMOD:
        set_dword(data_stack, dsp-3, get_dword(data_stack, dsp-3)
                                     % get_dword(data_stack, dsp-1));
        dsp-=2;
        break;
      case DEQ:
        set_dword(data_stack, dsp-3, get_dword(data_stack, dsp-3)
                                     == get_dword(data_stack, dsp-1));
        dsp-=2;
        break;
      case DLT:
        set_dword(data_stack, dsp-3, get_dword(data_stack, dsp-3)
                                     < get_dword(data_stack, dsp-1));
        dsp-=2;
        break;
      case DGT:
        set_dword(data_stack, dsp-3, get_dword(data_stack, dsp-3)
                                     > get_dword(data_stack, dsp-1));
        dsp-=2;
        break;

      /// Unsigned Double Arithmetic and Comparisson ///
      case DDIVU:
        set_dword(data_stack, dsp-3, (DWORDU)get_dword(data_stack, dsp-3)
                                     / (DWORDU)get_dword(data_stack, dsp-1));
        dsp-=2;
        break;
      case DMODU:
        set_dword(data_stack, dsp-3, (DWORDU)get_dword(data_stack, dsp-3)
                                     % (DWORDU)get_dword(data_stack, dsp-1));
        dsp-=2;
        break;
      case DLTU:
        set_dword(data_stack, dsp-3, (DWORDU)get_dword(data_stack, dsp-3)
                                     < (DWORDU)get_dword(data_stack, dsp-1));
        dsp-=2;
        break;
      case DGTU:
        set_dword(data_stack, dsp-3, (DWORDU)get_dword(data_stack, dsp-3)
                                     > (DWORDU)get_dword(data_stack, dsp-1));
        dsp-=2;
        break;

      /// Bitwise words ///
      case SL:
        data_stack[dsp-1] = data_stack[dsp-1] << data_stack[dsp];
        dsp--;
        break;
      case SR:
        data_stack[dsp-1] = data_stack[dsp-1] >> data_stack[dsp];
        dsp--;
        break;
      case AND:
        data_stack[dsp-1] = data_stack[dsp-1] & data_stack[dsp];
        dsp--;
        break;
      case OR:
        data_stack[dsp-1] = data_stack[dsp-1] | data_stack[dsp];
        dsp--;
        break;
      case NOT:
        data_stack[dsp] = ~data_stack[dsp];
        break;

      /// Bitwise double words ///
      case DSL:
        set_dword(data_stack, dsp-2, get_dword(data_stack, dsp-2)
                                     << data_stack[dsp]);
        dsp--;
        break;
      case DSR:
        set_dword(data_stack, dsp-2, get_dword(data_stack, dsp-2)
                                     >> data_stack[dsp]);
        dsp--;
        break;
      case DAND:
        set_dword(data_stack, dsp-3, get_dword(data_stack, dsp-3)
                                     & get_dword(data_stack, dsp-1));
        dsp-=2;
        break;
      case DOR:
        set_dword(data_stack, dsp-3, get_dword(data_stack, dsp-3)
                                     | get_dword(data_stack, dsp-1));
        dsp-=2;
        break;
      case DNOT:
        set_dword(data_stack, dsp-1, ~get_dword(data_stack, dsp-1));
        break;

      /// Movement ///
      case JUMP:
        pc = data_stack[dsp--];
        continue;
      case BRANCH:
        atemp = data_stack[dsp--];
        temp = data_stack[dsp--];
        if (temp) {
          pc = atemp;
          continue;
        }
        break;
      case CALL:
        return_stack[++rsp] = pc + 1;
        pc = data_stack[dsp--];
        continue;
      case RET:
        pc = return_stack[rsp--];
        continue;
      case DSP:
        data_stack[dsp+1] = dsp;
        dsp++;
        break;
      case PC:
        data_stack[++dsp] = pc;
        break;
      case BFP:
        data_stack[++dsp] = bfp;
        break;
      case FMP:
        data_stack[++dsp] = fmp;
        break;

      /// Number Printing ///
      case WPRN:
        printf("%hd", data_stack[dsp--]);
        break;
      case DPRN:
        printf("%d", get_dword(data_stack, dsp-1));
        dsp-=2;
        break;
      case WPRNU:
        printf("%hu", data_stack[dsp--]);
        break;
      case DPRNU:
        printf("%u", get_dword(data_stack, dsp-1));
        dsp-=2;
        break;
      case FPRN:
        printf("%.3lf", (double)get_dword(data_stack, dsp-1)
                        / SCALES[ DEFAULT_SCALE ]);
        dsp-=2;
        break;
      case FPRNSC:
        temp = data_stack[dsp--];
        if (temp && temp < SCALE_MAX) {
          dtemp = SCALES[temp];
        } else {
          dtemp = SCALES[ DEFAULT_SCALE ];
          temp = DEFAULT_SCALE;
        }
        printf("%.*lf", temp, (double)get_dword(data_stack, dsp-1) / dtemp);
        dsp-=2;
        break;

      /// Char and String printing ///
      case PRNCH:
        printf("%c", data_stack[dsp--] & 0xff);
        break;
      case PRNPK:
        temp = data_stack[dsp--];
        printf("%c", temp & 0xff);
        printf("%c", (temp >> BYTE_SIZE) & 0xff);
        break;
      case PRN:
        // Print from bfp to first null or buffer end
        print_string(memory, bfp, fmp);
        break;
      case PRNLN:
        // Print from bfp to first null or buffer end with a newline
        print_string(memory, bfp, fmp);
        printf("\n");
        break;
      case PRNMEM:
        atemp = data_stack[dsp--];
        if (memory[pc] >> BYTE_SIZE & 1) {
          print_string(memory, atemp, END_MEMORY);
        } else {
          print_string(memory, fmp + atemp, END_MEMORY);
        } 
        break;

      /// Reading ///
      case WREAD:
        dtemp = scanf("%hd", &temp);
        if (dtemp == EOF) {
          eof = true;
          data_stack[++dsp] = 0;
        } else {
          data_stack[++dsp] = temp;
        }
        if (DEBUGGING) getchar();
        break;
      case DREAD:
        {
          int res = scanf("%d", &dtemp);
          if (res == EOF) {
            eof = true;
            set_dword(data_stack, dsp + 1, 0);
          } else {
            set_dword(data_stack, dsp + 1, dtemp);
          }
          dsp+=2;
        }
        if (DEBUGGING) getchar();
        break;
      case FREAD:
        {
          double x;
          dtemp = scanf("%lf", &x);
          if (dtemp == EOF) {
            eof = true;
            set_dword(data_stack, dsp + 1, 0);
          } else {
            set_dword(data_stack, dsp + 1, (DWORD)(x * SCALES[ DEFAULT_SCALE ]));
          }
          dsp+=2;
        }
        if (DEBUGGING) getchar();
        break;
      case FREADSC:
        {
          // TODO no way to get scale off of stack?
          temp = data_stack[dsp--];
          if (temp && temp < SCALE_MAX) {
            dtemp = SCALES[temp];
          } else {
            dtemp = SCALES[ DEFAULT_SCALE ];
          }
          double x;
          int res = scanf("%lf", &x);
          if (res == EOF) {
            eof = true;
            set_dword(data_stack, dsp + 1, 0);
          } else {
            set_dword(data_stack, dsp + 1, (DWORD)(x * dtemp));
          }
          dsp+=2;
        }
        if (DEBUGGING) getchar();
        break;
      case READCH:
        {
          char ch = getchar();
          if (ch == EOF) {
            eof = true;
            data_stack[++dsp] = 0;
          } else {
            data_stack[++dsp] = (WORD)ch & 0xff;
          }
        }
        if (DEBUGGING) getchar();
        break;
      case READLN:
        temp = read_string(memory, bfp, fmp);
        if (temp == -1) {
          eof = true;
        } else {
          data_stack[++dsp] = temp;
        }
        break;

      /// Buffer and Chars ///
      case BFSTORE:
        atemp = data_stack[dsp--];
        memory[bfp + atemp] = data_stack[dsp--];
        break;
      case BFLOAD:
        atemp = data_stack[dsp];
        data_stack[dsp] = memory[bfp + atemp];
        break;
      case HIGH:
        data_stack[dsp+1] = (data_stack[dsp] >> BYTE_SIZE) & 0xff;
        dsp++;
        break;
      case LOW:
        data_stack[dsp+1] = data_stack[dsp] & 0xff;
        dsp++;
        break;
      case UNPACK:
        temp = data_stack[dsp];
        data_stack[++dsp] =  (temp >> BYTE_SIZE) & 0xff;
        data_stack[++dsp] = temp & 0xff;
        break;
      case PACK:
        temp = data_stack[dsp--];
        data_stack[dsp] = temp | (data_stack[dsp] << BYTE_SIZE);
        break;

      /// Memory copying ///
      case MEMCOPY:
        utemp = data_stack[dsp--];
        switch (memory[pc] >> BYTE_SIZE) {
          case MEM_BUF:
            atemp = data_stack[dsp--];
            memcpy(memory + bfp,
                   memory + fmp + atemp,
                   utemp * 2);
            break;
          case BUF_MEM:
            atemp = data_stack[dsp--];
            memcpy(memory + fmp + atemp,
                   memory + bfp,
                   utemp * 2);
            break;
        }
        break;
      case STRCOPY:
        switch (memory[pc] >> BYTE_SIZE) {
          case MEM_BUF:
            atemp = data_stack[dsp--];
            utemp = string_length(memory, fmp + atemp);
            memcpy(memory + bfp,
                   memory + fmp + atemp,
                   utemp * 2);
            break;
          case BUF_MEM:
            atemp = data_stack[dsp--];
            utemp = string_length(memory, bfp);
            memcpy(memory + fmp + atemp,
                   memory + bfp,
                   utemp * 2);
            break;
        }
        break;

      /// Fixed point arithmetic ///
      // only for those operations that cannot be done by dword ops
      case FMULT:
        {
          long long inter = (long long)get_dword(data_stack, dsp-3)
                            * (long long)get_dword(data_stack, dsp-1);
          dsp-=2;
          set_dword(data_stack, dsp-1, inter / SCALES[ DEFAULT_SCALE ]);
        }
        break;
      case FDIV:
        {
          double inter = (double)get_dword(data_stack, dsp-3)
                         / (double)get_dword(data_stack, dsp-1);
          dsp-=2;
          set_dword(data_stack, dsp-1, inter * SCALES[ DEFAULT_SCALE ]);
        }
        break;
      case FMULTSC:
        {
          temp = data_stack[dsp--];
          if (temp && temp < SCALE_MAX) {
            dtemp = SCALES[temp];
          } else {
            dtemp = SCALES[ DEFAULT_SCALE ];
          }
          long long inter = (long long)get_dword(data_stack, dsp-3)
                            * (long long)get_dword(data_stack, dsp-1);
          dsp-=2;
          set_dword(data_stack, dsp-1, inter / dtemp);
        }
        break;
      case FDIVSC:
        {
          temp = data_stack[dsp--];
          if (temp && temp < SCALE_MAX) {
            dtemp = SCALES[temp];
          } else {
            dtemp = SCALES[ DEFAULT_SCALE ];
          }
          double inter = (double)get_dword(data_stack, dsp-3)
                         / (double)get_dword(data_stack, dsp-1);
          dsp-=2;
          set_dword(data_stack, dsp-1, inter * dtemp);
        }
        break;

      /// Other ///
      case READCH_BF:
        {
          char ch;
          scanf("%c", &ch);
          atemp = data_stack[dsp--];
          if (atemp % 2 == 0) {
            memory[bfp + (atemp / 2)] = ch;
          } else {
            memory[bfp + (atemp / 2)] |= (ch << BYTE_SIZE);
            memory[bfp + (atemp / 2) + 1] = 0;
          }
        }
        break;
      case STREQ:
        {
          atemp = data_stack[dsp--];
          utemp = data_stack[dsp--];
          size_t i = 0;

          while (i + atemp < END_MEMORY && i + utemp < END_MEMORY) {
            WORDU chs1, chs2;
            chs1 = memory[atemp + i];
            chs2 = memory[utemp + i];

            if (chs1 != chs2) {
              data_stack[++dsp] = 0;
              break;
            } else if (chs1 == 0  // whole thing is 0
                       || (chs1 & 0x00ff) == 0     // first byte is 0
                       || (chs1 & 0xff00) == 0) {  // second byte is 0
              data_stack[++dsp] = 1;
              break;
            }
            i++;
          }
        }
        break;
      case MEMEQ:
        {
          WORDU size = data_stack[dsp--];
          WORDU first = data_stack[dsp--];
          WORDU second = data_stack[dsp--];
          data_stack[++dsp] = mem_equal(memory, first, second, size);
        }
        break;
      case IS_EOF:
        data_stack[++dsp] = eof ? 1 : 0;
        break;
      case RESET_EOF:
        eof = false;
        break;

      /// BAD OP CODE ///
      default:
        fprintf(stderr, "Error: Unknown OP code: 0x%hx\n", memory[pc]);
        return EXIT_OP;
    }
    pc++;
  }

  // When program is run for tests we print out the contents of the stack
  // to stdout to check that the program ended in the expected state.
  // Because we always increment dsp before pushing a value the true start of
  // the stack is index 1 and not 0.
  if (TESTING) {
    display_range(data_stack, 0x0001, dsp + 1, false);
  }

  return EXIT_SUCCESS;
}

/// main.c ///////////////////////////////////////////////////////////////////
size_t prog_length();
void set_program(WORD* mem, size_t length);

int main( int argc, char *argv[] ) {
  // VM memory pointers
  WORD* data_stack;
  WORD* return_stack;
  WORD* memory;

  // Allocate memory for the Data Stack
  data_stack = (WORD*) calloc((size_t)END_STACK + 1, sizeof(WORD));
  if (data_stack == NULL) {
    fprintf(stderr, "Error: Could not allocate the Data Stack\n");
    exit(EXIT_MEM);
  }

  // Allocate memory for the Return Stack
  return_stack = (WORD*) calloc((size_t)END_RETURN + 1, sizeof(WORD));
  if (return_stack == NULL) {
    fprintf(stderr, "Error: Could not allocate the Return Stack\n");
    exit(EXIT_MEM);
  }

  // Allocate memory for the Main Memory
  memory = (WORD*) calloc((size_t)END_MEMORY + 1, sizeof(WORD));
  if (memory == NULL) {
    fprintf(stderr, "Error: Could not allocate Main Program Memory\n");
    exit(EXIT_MEM);
  }

  // Load the program
  size_t length = prog_length();
  if (!length) {
    fprintf(stderr, "Error: program length is 0\n");
    exit(EXIT_FILE);
  } else if ((length / 2) + 1 >= END_MEMORY) {
    fprintf(stderr, "Error: program is to large to fit in memory\n");
    exit(EXIT_MEM);
  }
  set_program(memory, length);

  // Run program
  size_t result = execute(memory, length, data_stack, return_stack);

  // clean up
  free(memory);
  free(data_stack);
  free(return_stack);

  return result;
}

/** sample of what needs to be written for this to create a binary for a
 * full program with vm and assembled bytes

size_t prog_length() { return 6; }

void set_program(WORD* mem, size_t length) {
  char prog[] = {
    1,
    0,
    100,
    0,
    69,
    0
  };
  memcpy(mem, prog, length);
};

*/
