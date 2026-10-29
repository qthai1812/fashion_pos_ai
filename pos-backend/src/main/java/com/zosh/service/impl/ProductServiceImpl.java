package com.zosh.service.impl;

import com.zosh.domain.UserRole;
import com.zosh.mapper.ProductMapper;
import com.zosh.modal.Category;
import com.zosh.modal.Product;
import com.zosh.modal.Store;
import com.zosh.modal.User;
import com.zosh.payload.dto.ProductDTO;
import com.zosh.repository.CategoryRepository;
import com.zosh.repository.ProductRepository;
import com.zosh.repository.StoreRepository;
import com.zosh.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final CategoryRepository categoryRepository;

    private void validateStoreAccess(User user, Store store) throws Exception {
        if (user == null || store == null) {
            throw new Exception("You do not have permission to manage products in this store");
        }
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        boolean isStoreOwner = store.getStoreAdmin() != null && store.getStoreAdmin().getId().equals(user.getId());
        boolean isSameStoreEmployee = user.getStore() != null && user.getStore().getId().equals(store.getId());
        boolean hasManagerRole = user.getRole() == UserRole.ROLE_STORE_ADMIN
                || user.getRole() == UserRole.ROLE_STORE_MANAGER;

        if (!hasManagerRole || (!isStoreOwner && !isSameStoreEmployee)) {
            throw new Exception("You do not have permission to manage products in this store");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductDTO createProduct(ProductDTO productDTO, User user) throws Exception {
        Store store = storeRepository.findById(productDTO.getStoreId()).orElseThrow(
                () -> new Exception("Store not found")
        );
        validateStoreAccess(user, store);

        Category category = categoryRepository.findById(productDTO.getCategoryId()).orElseThrow(
                () -> new Exception("Category not found")
        );
        Product product = ProductMapper.toEntity(productDTO, store, category);
        Product savedProduct = productRepository.save(product);
        return ProductMapper.toDTO(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDTO getProductById(Long id) throws Exception {
        Product product = productRepository.findById(id).orElseThrow(
                () -> new Exception("Product not found with id: " + id)
        );
        return ProductMapper.toDTO(product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductDTO updateProduct(Long id, ProductDTO productDTO, User user) throws Exception {
        Product product = productRepository.findById(id).orElseThrow(
                () -> new Exception("product not found")
        );
        validateStoreAccess(user, product.getStore());

        product.setName(productDTO.getName());
        product.setDescription(productDTO.getDescription());
        product.setSku(productDTO.getSku());
        product.setImage(productDTO.getImage());
        product.setMrp(productDTO.getMrp());
        product.setSellingPrice(productDTO.getSellingPrice());
        product.setBrand(productDTO.getBrand());
        product.setUpdatedAt(LocalDateTime.now());

        if (productDTO.getCategoryId() != null) {
            Category category = categoryRepository.findById(productDTO.getCategoryId()).orElseThrow(
                    () -> new Exception("category not found")
            );
            product.setCategory(category);
        }

        Product savedProduct = productRepository.save(product);
        return ProductMapper.toDTO(savedProduct);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long id, User user) throws Exception {
        Product product = productRepository.findById(id).orElseThrow(
                () -> new Exception("product not found")
        );
        validateStoreAccess(user, product.getStore());
        productRepository.delete(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDTO> getProductsByStoreId(Long storeId) {
        List<Product> products = productRepository.findByStoreId(storeId);
        return products.stream()
                .map(ProductMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDTO> searchByKeyword(Long storeId, String keyword) {
        List<Product> products = productRepository.searchByKeyword(storeId, keyword);
        return products.stream()
                .map(ProductMapper::toDTO)
                .collect(Collectors.toList());
    }
}