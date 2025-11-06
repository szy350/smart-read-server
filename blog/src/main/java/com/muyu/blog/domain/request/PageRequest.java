package com.muyu.blog.domain.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PageRequest {

    private Integer pageNum;
    private Integer pageSize;
}
