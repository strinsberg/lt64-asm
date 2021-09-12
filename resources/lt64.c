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

/// ltio.c ///////////////////////////////////////////////////////////////////
void display_range(WORD* mem, ADDRESS start, ADDRESS end, bool debug) {
  for (ADDRESS i = start; i < end; i++) {
    if (debug)
      fprintf(stderr, "%04hx ", mem[i]);
    else
      printf("%04hx ", mem[i]);
  }

  if (debug)
    fprintf(stderr, "\n");
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

void read_string(WORD* mem, ADDRESS start, ADDRESS max) {
  ADDRESS atemp = start;
  bool first = true;
  WORDU two_chars = 0;

  while (atemp < max - 1) {
    char ch;
    scanf("%c", &ch);
    fprintf(stderr, "%c", ch);

    if (ch == '\n') {
      if (first) {
        two_chars = 0;
      } else {
        two_chars &= 0xff;
      }
      mem[atemp] = two_chars;
      mem[atemp + 1] = 0;
      return;

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
}

/// ltrun.c //////////////////////////////////////////////////////////////////
enum op_codes { HALT=0,
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
};

enum copy_codes { MEM_BUF = 0, BUF_MEM };

size_t execute(WORD* memory, size_t length, WORD* data_stack, WORD* return_stack) {
  // Declare and initialize memory pointer "registers"
  ADDRESS dsp, rsp, pc, bfp, fmp;
  dsp = 0;
  rsp = 0;
  pc = 0;
  bfp = length;
  fmp = length + BUFFER_SIZE;

  // Declare some temporary "registers" for working with intermediate values
  ADDRESS atemp;
  WORD temp;
  WORDU utemp;
  DWORD dtemp;
  DWORDU udtemp;
  
  // Run the program in memory
  bool run = true;
  while (run) {
    // Print stack, op code, and pc before every execution
    if (DEBUGGING) {
      fprintf(stderr, "Stack: ");
      display_range(data_stack, 0x0001, dsp + 1, DEBUGGING);
      fprintf(stderr, "OP: %hx (%hu)\nPC: %hx (%hu)\n\n",
              memory[pc], memory[pc], pc, pc);
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
        temp = (memory[pc] >> BYTE_SIZE);
        if (temp) {
          data_stack[dsp] = data_stack[dsp] << temp;
        } else {
          data_stack[dsp-1] = data_stack[dsp-1] << data_stack[dsp];
          dsp--;
        }
        break;
      case SR:
        temp = (memory[pc] >> BYTE_SIZE);
        if (temp) {
          data_stack[dsp] = data_stack[dsp] >> temp;
        } else {
          data_stack[dsp-1] = data_stack[dsp-1] >> data_stack[dsp];
          dsp--;
        }
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
        temp = (memory[pc] >> BYTE_SIZE);
        if (temp) {
          set_dword(data_stack, dsp-1, get_dword(data_stack, dsp-1) << temp);
        } else {
          set_dword(data_stack, dsp-2, get_dword(data_stack, dsp-2)
                                       << data_stack[dsp]);
          dsp--;
        }
        break;
      case DSR:
        temp = (memory[pc] >> BYTE_SIZE);
        if (temp) {
          set_dword(data_stack, dsp-1, get_dword(data_stack, dsp-1) >> temp);
        } else {
          set_dword(data_stack, dsp-2, get_dword(data_stack, dsp-2)
                                       >> data_stack[dsp]);
          dsp--;
        }
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
        utemp = (memory[pc] >> BYTE_SIZE);
        if (utemp) {
          pc += utemp;
        } else {
          pc = data_stack[dsp--];
        }
        continue;
      case BRANCH:
        utemp = (memory[pc] >> BYTE_SIZE);
        if (utemp) {
          temp = data_stack[dsp--];
          if (temp) {
            pc += utemp;
            continue;
          }
        } else {
          atemp = data_stack[dsp--];
          temp = data_stack[dsp--];
          if (temp) {
            pc = atemp;
            continue;
          }
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
        data_stack[dsp+1] = dsp + (memory[pc] >> BYTE_SIZE);
        dsp++;
        break;
      case PC:
        data_stack[++dsp] = pc + (memory[pc] >> BYTE_SIZE);
        break;
      case BFP:
        data_stack[++dsp] = bfp + (memory[pc] >> BYTE_SIZE);
        break;
      case FMP:
        data_stack[++dsp] = fmp + (memory[pc] >> BYTE_SIZE);
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
        // TODO no way to get scale off of stack?
        temp = (memory[pc] >> BYTE_SIZE);
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
        scanf("%hd", &temp);
        data_stack[++dsp] = temp;
        break;
      case DREAD:
        scanf("%d", &dtemp);
        set_dword(data_stack, dsp + 1, dtemp);
        dsp+=2;
        break;
      case FREAD:
        {
          double x;
          scanf("%lf", &x);
          set_dword(data_stack, dsp + 1, (DWORD)(x * SCALES[ DEFAULT_SCALE ]));
          dsp+=2;
        }
        break;
      case FREADSC:
        {
          // TODO no way to get scale off of stack?
          temp = (memory[pc] >> BYTE_SIZE);
          if (temp && temp < SCALE_MAX) {
            dtemp = SCALES[temp];
          } else {
            dtemp = SCALES[ DEFAULT_SCALE ];
          }
          double x;
          scanf("%lf", &x);
          set_dword(data_stack, dsp + 1, (DWORD)(x * dtemp));
          dsp+=2;
        }
        break;
      case READCH:
        scanf("%c", &temp);
        data_stack[++dsp] = temp & 0xff;
        break;
      case READLN:
        read_string(memory, bfp, fmp);
        break;

      /// Buffer and Chars ///
      case BFSTORE:
        temp = (memory[pc] >> BYTE_SIZE);
        if (temp) {
          memory[bfp + (temp - 1)] = data_stack[dsp--];
        } else {
          atemp = data_stack[dsp--];
          memory[bfp + atemp] = data_stack[dsp--];
        }
        break;
      case BFLOAD:
        temp = (memory[pc] >> BYTE_SIZE);
        if (temp) {
          data_stack[++dsp] = memory[bfp + (temp - 1)];
        } else {
          atemp = data_stack[dsp];
          data_stack[dsp] = memory[bfp + atemp];
        }
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
          // TODO no way to get scale of stack?
          temp = (memory[pc] >> BYTE_SIZE);
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
          // TODO no way to get scale of stack?
          temp = (memory[pc] >> BYTE_SIZE);
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
