// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <cstdlib>

#include "shader_recompiler/frontend/maxwell/translate/impl/impl.h"

namespace Shader::Maxwell {

// AriseOS: set by the pipeline cache while it builds the disk cache at boot. Tensor programs are
// left for when the game asks for them, so "Loading shaders" does not wait minutes on Turnip
// compiling Ocarina's movie network (now skipped at run time, ocarina_movie.h).
thread_local bool arise_preloading_pipelines = false;

void TranslatorVisitor::TENSOR_MUL_ADD(u64 insn) {
    union {
        u64 raw;
        BitField<0, 8, IR::Reg> dest_reg;
        BitField<8, 8, IR::Reg> matrix_a;
        BitField<20, 8, IR::Reg> matrix_b;
        BitField<39, 8, IR::Reg> matrix_c;
    } const tensor{insn};

    const IR::Reg ar = tensor.matrix_a;
    const IR::Reg br = tensor.matrix_b;
    const IR::Reg cr = tensor.matrix_c;
    const IR::Reg dr = tensor.dest_reg;

    const u64 ai = static_cast<u64>(ar);
    const u64 bi = static_cast<u64>(br);
    const u64 ci = static_cast<u64>(cr);
    const u64 di = static_cast<u64>(dr);
    const bool has_c = ci != 255;
    // AriseOS: Ocarina of Time draws its 3D through its own neural network (matrix multiplies on
    // every frame): with them rejected the world stayed black. They run emulated, as on PC; the
    // first boot compiles them for a few minutes, then they come from the pipeline cache.
    // EDEN_NX2_NO_TENSOR=1 turns them off.
    static const bool disabled{std::getenv("EDEN_NX2_NO_TENSOR") != nullptr};
    if (disabled || arise_preloading_pipelines || ((insn >> 48) & 1) == 0) {
        throw NotImplementedException("Unsupported matrix multiply encoding");
    }
    const bool short_depth = (insn >> 49) & 1;

    const IR::U32 a0 = X(ar);
    const IR::U32 a1 = X(IR::Reg{ai + 1});
    const IR::U32 a2 = short_depth ? ir.Imm32(0u) : X(IR::Reg{ai + 2});
    const IR::U32 a3 = short_depth ? ir.Imm32(0u) : X(IR::Reg{ai + 3});
    const IR::U32 b0 = X(br);
    const IR::U32 b1 = short_depth ? ir.Imm32(0u) : X(IR::Reg{bi + 1});
    const IR::U32 c0 = has_c ? X(cr) : ir.Imm32(0u);
    const IR::U32 c1 = has_c ? X(IR::Reg{ci + 1}) : ir.Imm32(0u);

    const IR::Value result{ir.TensorMulAdd(
                                  ir.CompositeConstruct(a0, a1, a2, a3),
                                  ir.CompositeConstruct(b0, b1), ir.CompositeConstruct(c0, c1),
                                  short_depth ? 8U : 16U)};
    X(dr, IR::U32{ir.CompositeExtract(result, 0)});
    X(IR::Reg{di + 1}, IR::U32{ir.CompositeExtract(result, 1)});
}

} // namespace Shader::Maxwell
