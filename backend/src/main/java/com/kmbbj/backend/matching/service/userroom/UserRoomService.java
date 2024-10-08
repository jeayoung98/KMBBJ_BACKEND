package com.kmbbj.backend.matching.service.userroom;

import com.kmbbj.backend.auth.entity.User;
import com.kmbbj.backend.matching.entity.Room;
import com.kmbbj.backend.matching.entity.UserRoom;

import java.util.Optional;

public interface UserRoomService {
    void save(UserRoom userRoom);

    Optional<UserRoom> findByUserAndRoom(User user, Room room);

    UserRoom deleteUserFromRoom(Long roomId);

    UserRoom findCurrentRoom();
}
