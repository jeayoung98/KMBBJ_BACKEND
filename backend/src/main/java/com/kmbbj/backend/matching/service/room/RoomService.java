package com.kmbbj.backend.matching.service.room;

import com.kmbbj.backend.auth.entity.User;
import com.kmbbj.backend.matching.dto.*;
import com.kmbbj.backend.matching.entity.Room;
import org.springframework.data.domain.Page;

import java.util.List;

public interface RoomService {
    Room createRoom(CreateRoomDTO createRoomDTO, User user);

    void editRoom(Long roomId, EditRoomDTO editRoomDTO);

    void deleteRoom(Long roomId);

    Page<RoomListDTO> searchRoomsByTitle(SearchingRoomDTO searchingRoomDTO);

    Page<RoomListDTO> findAll(SortConditionDTO sortConditionDTO);

    Room findById(Long roomId);

    void startGame(Long roomId);

    void enterRoom(Long roomId);

    void quitRoom(Long roomId);

    List<Room> findRoomsWithinAssetRange(Long asset, Long range);

    Room findRoomByLatestCreateDate();

    EnterRoomDTO getEnterRoomDto(Room room);
}
