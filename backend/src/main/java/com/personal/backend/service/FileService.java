package com.personal.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.backend.common.BizException;
import com.personal.backend.common.UserContext;
import com.personal.backend.entity.NoteFile;
import com.personal.backend.mapper.NoteFileMapper;
import com.personal.backend.utils.OwnedUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 文件 Service：上传/删除附件（学习记录）、上传头像
 * 文件存本地磁盘 uploads/ 目录，数据库只存相对路径
 */
@Service
@RequiredArgsConstructor
public class FileService {

    /** 允许的附件类型：pdf + 图片 */
    private static final String[] ALLOWED_EXTENSIONS = {"pdf", "png", "jpg", "jpeg", "doc", "docx"};

    /** 图片最大 2MB */
    private static final long MAX_IMAGE_SIZE = 2 * 1024 * 1024;

    /** 附件最大 10MB */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final NoteFileMapper noteFileMapper;

    @Value("${app.upload-path}")
    private String uploadPath;

    /**
     * 上传学习附件：写入 uploads/note/yyyy/MM/dd/ 目录
     */
    public NoteFile uploadNoteFile(MultipartFile file, Long learnRecordId) {
        Long userId = UserContext.requireUserId();

        if (file == null || file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException("附件大小不能超过 10MB");
        }
        String ext = getExtension(file.getOriginalFilename());
        if (!isAllowed(ext)) {
            throw new BizException("不支持的文件类型，仅支持：pdf/png/jpg/doc/docx");
        }

        // 存储路径：note/2026/08/18/{uuid}.pdf
        LocalDate today = LocalDate.now();
        String relDir = String.format("note/%d/%02d/%02d", today.getYear(), today.getMonthValue(), today.getDayOfMonth());
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String relPath = relDir + "/" + storedName;

        try {
            Path target = Paths.get(uploadPath, relPath).normalize();
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new BizException("文件保存失败：" + e.getMessage());
        }

        NoteFile noteFile = new NoteFile();
        noteFile.setUserId(userId);
        noteFile.setLearnRecordId(learnRecordId);
        noteFile.setFileName(file.getOriginalFilename());
        noteFile.setFilePath(relPath);
        noteFile.setFileType(ext);
        noteFile.setFileSize(file.getSize());
        noteFileMapper.insert(noteFile);
        return noteFile;
    }

    /**
     * 删除学习附件：删数据库记录 + 物理删文件
     */
    public void deleteNoteFile(Long id) {
        Long userId = UserContext.requireUserId();
        NoteFile noteFile = OwnedUtil.requireOwned(noteFileMapper, id, userId,
                NoteFile::getUserId, "附件不存在");
        noteFileMapper.deleteById(id);
        deletePhysicalFile(noteFile.getFilePath());
    }

    /**
     * 查询某学习记录的附件列表
     */
    public List<NoteFile> listByLearnRecord(Long learnRecordId) {
        Long userId = UserContext.requireUserId();
        return noteFileMapper.selectList(
                new LambdaQueryWrapper<NoteFile>()
                        .eq(NoteFile::getLearnRecordId, learnRecordId)
                        .eq(NoteFile::getUserId, userId)
                        .orderByAsc(NoteFile::getId));
    }

    /**
     * 上传头像：写入 uploads/avatar/ 目录，返回相对路径
     */
    public String uploadAvatar(MultipartFile file) {
        Long userId = UserContext.requireUserId();

        if (file == null || file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BizException("头像大小不能超过 2MB");
        }
        String ext = getExtension(file.getOriginalFilename());
        if (!Set.of("png", "jpg", "jpeg").contains(ext)) {
            throw new BizException("头像仅支持 png/jpg 格式");
        }

        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String relPath = "avatar/" + userId + "/" + storedName;

        try {
            Path target = Paths.get(uploadPath, relPath).normalize();
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new BizException("头像保存失败：" + e.getMessage());
        }
        return relPath;
    }

    /**
     * 删除旧头像物理文件（更换头像后清理，避免头像长期堆积）
     * 仅允许删除 uploads/avatar/ 目录下的文件；路径为空或异常（含穿越）时静默跳过
     */
    public void deleteAvatar(String relPath) {
        if (!StringUtils.hasText(relPath)) {
            return;
        }
        Path base = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path target = base.resolve(relPath).normalize();
        // 必须落在 uploads 根内，且位于 avatar/ 子目录（防路径穿越删除其它文件）
        if (!target.startsWith(base) || !target.startsWith(base.resolve("avatar"))) {
            return;
        }
        deletePhysicalFile(relPath);
    }

    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    private boolean isAllowed(String ext) {
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equals(ext)) {
                return true;
            }
        }
        return false;
    }

    private void deletePhysicalFile(String relPath) {
        try {
            Files.deleteIfExists(Paths.get(uploadPath, relPath).normalize());
        } catch (IOException e) {
            // 物理文件删除失败不阻断业务，仅记录
        }
    }
}
