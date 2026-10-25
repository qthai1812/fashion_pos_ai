import React from "react";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Search, Filter } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";

const InventoryFilters = ({
  searchTerm,
  onSearch,
  category,
  onCategoryChange,
  products,
  inventoryRows,
}) => {
  const totalQty = inventoryRows.reduce((sum, row) => sum + (row.quantity || 0), 0);
  const lowStockCount = inventoryRows.filter((r) => r.quantity > 0 && r.quantity <= 5).length;
  const outOfStockCount = inventoryRows.filter((r) => r.quantity === 0).length;

  return (
    <Card>
      <CardContent className="p-6">
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3 items-center">
          {/* Search */}
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
            <Input
              type="search"
              placeholder="Search by name..."
              className="pl-9"
              value={searchTerm}
              onChange={onSearch}
            />
          </div>

          {/* Category Filter */}
          <div className="relative">
            <Select value={category} onValueChange={onCategoryChange}>
              <SelectTrigger
                startIcon={<Filter className="h-4 w-4 text-gray-500" />}
                className="w-full"
              >
                <SelectValue placeholder="All Categories" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Categories</SelectItem>
                {[
                  ...new Set(
                    products.map((p) => p.category?.name).filter(Boolean)
                  ),
                ].map((cat) => (
                  <SelectItem key={cat} value={cat}>
                    {cat}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Summary Stats */}
          <div className="flex items-center gap-3 border p-3 rounded-md">
            <h3 className="text-lg font-medium text-gray-500">Total Quantity :</h3>
            <p className="text-xl font-bold text-green-600">{totalQty}</p>
          </div>
        </div>
      </CardContent>
    </Card>
  );
};

export default InventoryFilters;
