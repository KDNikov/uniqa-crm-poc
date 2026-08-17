package com.uniqa.crmpoc.controller;

import com.uniqa.crmpoc.domain.Category;
import com.uniqa.crmpoc.dto.CategoryRequest;
import com.uniqa.crmpoc.repository.CategoryRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public List<Category> listAll() {
        return categoryRepository.findAll();
    }

    @PostMapping
    public Category create(@Valid @RequestBody CategoryRequest req) {
        Category category = new Category();
        category.setName(req.name());
        category.setDescription(req.description());
        return categoryRepository.save(category);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        categoryRepository.deleteById(id);
    }
}
