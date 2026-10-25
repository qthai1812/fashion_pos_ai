import React from "react";
import {
  Table,
  TableHeader,
  TableRow,
  TableHead,
  TableBody,
  TableCell,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Edit, AlertTriangle, TrendingDown } from "lucide-react";

const getStockBadge = (quantity) => {
  if (quantity === 0)
    return <Badge variant="destructive" className="text-xs">Out of Stock</Badge>;
  if (quantity <= 5)
    return <Badge variant="outline" className="text-xs text-amber-600 border-amber-400 bg-amber-50">Low Stock</Badge>;
  return <Badge variant="outline" className="text-xs text-green-600 border-green-400 bg-green-50">In Stock</Badge>;
};

const InventoryTable = ({ rows, onEdit }) => (
  <Table>
    <TableHeader>
      <TableRow>
        <TableHead className="w-[110px]">SKU</TableHead>
        <TableHead>Product Name</TableHead>
        <TableHead>Category</TableHead>
        <TableHead className="text-center">Quantity</TableHead>
        <TableHead className="text-center">Stock Status</TableHead>
        <TableHead className="text-right">Action</TableHead>
      </TableRow>
    </TableHeader>
    <TableBody>
      {rows.length > 0 ? (
        rows.map((row) => (
          <TableRow key={row?.id}>
            {/* SKU */}
            <TableCell className="font-mono text-sm font-medium text-muted-foreground">
              {row.sku || "—"}
            </TableCell>

            {/* Product Name + ID */}
            <TableCell>
              <div>
                <p className="font-medium text-sm leading-tight">
                  {row.name || "Unknown Product"}
                </p>
                <p className="text-xs text-muted-foreground mt-0.5">
                  ID: {row.productId}
                </p>
              </div>
            </TableCell>

            {/* Category */}
            <TableCell>
              {row.category?.name ? (
                <Badge variant="secondary" className="text-xs font-normal">
                  {row.category.name}
                </Badge>
              ) : (
                <span className="text-muted-foreground text-sm">—</span>
              )}
            </TableCell>

            {/* Quantity */}
            <TableCell className="text-center">
              <div className="flex items-center justify-center gap-1.5">
                {row.quantity === 0 && (
                  <AlertTriangle className="h-3.5 w-3.5 text-destructive" />
                )}
                {row.quantity > 0 && row.quantity <= 5 && (
                  <TrendingDown className="h-3.5 w-3.5 text-amber-500" />
                )}
                <span
                  className={`font-semibold text-sm tabular-nums ${
                    row.quantity === 0
                      ? "text-destructive"
                      : row.quantity <= 5
                      ? "text-amber-600"
                      : ""
                  }`}
                >
                  {row.quantity ?? 0}
                </span>
              </div>
            </TableCell>

            {/* Stock Status Badge */}
            <TableCell className="text-center">
              {getStockBadge(row.quantity ?? 0)}
            </TableCell>

            {/* Action */}
            <TableCell className="text-right">
              <Button size="sm" variant="outline" onClick={() => onEdit(row)}>
                <Edit className="h-4 w-4" />
              </Button>
            </TableCell>
          </TableRow>
        ))
      ) : (
        <TableRow>
          <TableCell colSpan={6} className="text-center py-8 text-muted-foreground">
            No inventory found matching your criteria
          </TableCell>
        </TableRow>
      )}
    </TableBody>
  </Table>
);

export default InventoryTable;
