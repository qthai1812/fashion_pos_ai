package com.zosh.service.impl;

import com.zosh.domain.UserRole;
import com.zosh.mapper.CategoryMapper;
import com.zosh.modal.Category;
import com.zosh.modal.Store;
import com.zosh.modal.User;
import com.zosh.payload.dto.CategoryDTO;
import com.zosh.repository.CategoryRepository;
import com.zosh.repository.StoreRepository;
import com.zosh.service.CategoryService;
import com.zosh.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserService userService;
    private final StoreRepository storeRepository;

    @Override
    public CategoryDTO createCategory(CategoryDTO dto) throws Exception {
        User user = userService.getCurrentUser();

        Store store = storeRepository.findById(dto.getStoreId()).orElseThrow(
                () -> new Exception("Store not found")
        );

        Category category = Category.builder()
                .store(store)
                .name(dto.getName())
                .build();
        checkAuthority(user, category.getStore());
        return CategoryMapper.toDTO(categoryRepository.save(category));
    }

    @Override
    public List<CategoryDTO> getCategoriesByStore(Long storeId) {
        List<Category>  categories = categoryRepository.findByStoreId(storeId);
        return categories.stream().map(
                CategoryMapper::toDTO
        ).collect(Collectors.toList());
    }

    @Override
    public CategoryDTO updateCategory(Long id, CategoryDTO dto) throws Exception {
        Category category = categoryRepository.findById(id).orElseThrow(
                () -> new Exception("category not exist")
        );
        User user = userService.getCurrentUser();
        category.setName(dto.getName());
        checkAuthority(user, category.getStore());
        return CategoryMapper.toDTO(categoryRepository.save(category));
    }

    @Override
    public void deleteCategory(Long id) throws Exception {
        Category category = categoryRepository.findById(id).orElseThrow(
                () -> new Exception("category not exist")
        );
        User user = userService.getCurrentUser();
        checkAuthority(user, category.getStore());
        categoryRepository.delete(category);
    }

    private void checkAuthority(User user, Store store) throws Exception {
        if (user == null || store == null) {
            throw new Exception("you don't have permission to manage this category");
        }
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        boolean isStoreOwner = user.getRole() == UserRole.ROLE_STORE_ADMIN
                && store.getStoreAdmin() != null
                && store.getStoreAdmin().getId().equals(user.getId());
        boolean isSameStoreManager = (user.getRole() == UserRole.ROLE_STORE_MANAGER || user.getRole() == UserRole.ROLE_STORE_ADMIN)
                && user.getStore() != null
                && user.getStore().getId().equals(store.getId());

        if (!isStoreOwner && !isSameStoreManager) {
            throw new Exception("you don't have permission to manage this category");
        }
    }
}
