package com.llpop.koil.ui.screen

import com.llpop.koil.data.oil.Provinces



// 设置页与总览页各自原本有一份 file-private 的同一份表；拆文件后会撞名，
// 抽到这里由两页共享（内容不变，仍是 Provinces.ALL）。
internal val PROVINCES = Provinces.ALL
