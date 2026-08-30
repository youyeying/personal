package com.personal.backend.controller;

import com.personal.backend.common.Result;
import com.personal.backend.entity.NoteFile;
import com.personal.backend.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 文件上传接口：学习附件 / 头像
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /** 上传学习附件 */
    @PostMapping("/note")
    public Result<NoteFile> uploadNote(@RequestParam("file") MultipartFile file,
                                       @RequestParam("learnRecordId") Long learnRecordId) {
        return Result.ok(fileService.uploadNoteFile(file, learnRecordId), "上传成功");
    }

    /** 删除学习附件 */
    @DeleteMapping("/note/{id}")
    public Result<Void> deleteNote(@PathVariable Long id) {
        fileService.deleteNoteFile(id);
        return Result.ok(null, "删除成功");
    }

    /** 某学习记录的附件列表 */
    @GetMapping("/note/list")
    public Result<List<NoteFile>> listByLearnRecord(@RequestParam("learnRecordId") Long learnRecordId) {
        return Result.ok(fileService.listByLearnRecord(learnRecordId));
    }

    /** 上传头像 */
    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String path = fileService.uploadAvatar(file);
        return Result.ok(Map.of("path", path), "上传成功");
    }
}
