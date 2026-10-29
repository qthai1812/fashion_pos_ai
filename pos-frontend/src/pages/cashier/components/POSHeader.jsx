import React from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "../../../components/ui/button";
import { useSidebar } from "../../../context/hooks/useSidebar";

const POSHeader = () => {
  const { setSidebarOpen } = useSidebar();
  return (
    <div className="bg-background/95 backdrop-blur border-b border-border px-6 py-3.5 shadow-sm">
      <div className="grid grid-cols-12 items-center w-full">
        {/* 1. CỘT TRÁI (3/12): Nút mở Menu Sidebar */}
        <div className="col-span-3 flex items-center justify-start">
          <Button
            variant="outline"
            className="z-10 p-2 h-10 w-10 rounded-lg shadow-sm border-border hover:bg-muted"
            onClick={() => setSidebarOpen(true)}
            aria-label="Open sidebar"
          >
            <svg
              className="h-5 w-5"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.5"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M4 6h16M4 12h16M4 18h16"
              />
            </svg>
          </Button>
        </div>

        {/* 2. CỘT GIỮA (6/12): POS TERMINAL NỔI BẬT & CĂN GIỮA */}
        <div className="col-span-6 flex flex-col items-center justify-center text-center">
          <div className="inline-flex items-center gap-2.5 bg-emerald-500/10 border border-emerald-500/30 px-5 py-1.5 rounded-full shadow-sm hover:shadow-md transition-all">
            {/* Chấm tròn xanh nhấp nháy báo sẵn sàng */}
            <span className="relative flex h-2.5 w-2.5">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500"></span>
            </span>

            {/* Tiêu đề chính viết hoa, đậm, màu Gradient */}
            <h1 className="text-xl md:text-2xl font-black tracking-widest bg-gradient-to-r from-emerald-600 to-teal-700 bg-clip-text text-transparent uppercase">
              POS Terminal
            </h1>
          </div>
          {/* Subtitle bên dưới */}
          <p className="text-[10px] md:text-[11px] font-extrabold text-muted-foreground uppercase tracking-widest mt-1.5">
            Hệ Thống Bán Hàng Tại Quầy • Điểm Thu Ngân
          </p>
        </div>

        {/* 3. CỘT PHẢI (3/12): Khu vực phím tắt */}
        <div className="col-span-3 flex items-center justify-end">
          <Badge
            variant="outline"
            className="hidden lg:inline-flex py-1.5 px-3 bg-muted/30 shadow-sm font-medium"
          >
            <span className="font-mono font-bold text-foreground mr-1">
              F1:
            </span>{" "}
            Tìm kiếm
            <span className="text-border mx-2">|</span>
            <span className="font-mono font-bold text-foreground mr-1">
              F2:
            </span>{" "}
            Khuyến mãi
            <span className="text-border mx-2">|</span>
            <span className="font-mono font-bold text-foreground mr-1">
              Ctrl+Enter:
            </span>{" "}
            Thanh toán
          </Badge>
        </div>
      </div>
    </div>
  );
};

export default POSHeader;
